package io.myclaw.server.audit;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestAuditFilter extends OncePerRequestFilter {
    private static final long SLOW_REQUEST_MILLIS = 3_000;
    public static final String REQUEST_ID = "requestId";
    private static final Logger log = LoggerFactory.getLogger(RequestAuditFilter.class);

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\r\\n\\t]");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = normalizeRequestId(request.getHeader("X-Request-Id"));
        long startedAt = System.nanoTime();
        response.setHeader("X-Request-Id", requestId);
        MDC.put(REQUEST_ID, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (request.isAsyncStarted()) {
                registerAsyncAudit(request, response, requestId, startedAt);
            } else {
                logCompletion(request, response, requestId, startedAt, "completed");
            }
            MDC.remove(REQUEST_ID);
        }
    }

    private void registerAsyncAudit(HttpServletRequest request, HttpServletResponse response,
                                    String requestId, long startedAt) {
        AtomicBoolean logged = new AtomicBoolean();
        request.getAsyncContext().addListener(new AsyncListener() {
            @Override public void onComplete(AsyncEvent event) { once("completed"); }
            @Override public void onTimeout(AsyncEvent event) { once("timeout"); }
            @Override public void onError(AsyncEvent event) { once("error"); }
            @Override public void onStartAsync(AsyncEvent event) { }
            private void once(String outcome) {
                if (!logged.compareAndSet(false, true)) return;
                MDC.put(REQUEST_ID, requestId);
                try { logCompletion(request, response, requestId, startedAt, outcome); }
                finally { MDC.remove(REQUEST_ID); }
            }
        });
    }

    private static void logCompletion(HttpServletRequest request, HttpServletResponse response,
                                      String requestId, long startedAt, String outcome) {
        long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
        boolean failed = response.getStatus() >= 400 || "error".equals(outcome) || "timeout".equals(outcome);
        boolean slow = durationMillis >= SLOW_REQUEST_MILLIS;
        if (!failed && !slow) return;
        String result = switch (outcome) {
            case "timeout" -> "超时";
            case "error" -> "异常";
            default -> failed ? "失败" : "慢请求";
        };
        if (failed) {
            log.warn("请求审计 请求编号={} 请求方法={} 请求路径={} 状态码={} 执行结果={} 耗时毫秒={} 客户端地址={}",
                    requestId, request.getMethod(), safePath(request.getRequestURI()), response.getStatus(),
                    result, durationMillis, request.getRemoteAddr());
        } else {
            log.info("请求审计 请求编号={} 请求方法={} 请求路径={} 状态码={} 执行结果={} 耗时毫秒={} 客户端地址={}",
                    requestId, request.getMethod(), safePath(request.getRequestURI()), response.getStatus(),
                    result, durationMillis, request.getRemoteAddr());
        }
    }

    private static String normalizeRequestId(String value) {
        if (value != null && REQUEST_ID_PATTERN.matcher(value).matches()) return value;
        return UUID.randomUUID().toString();
    }

    private static String safePath(String value) {
        if (value == null) return "";
        return CONTROL_CHARS_PATTERN.matcher(value).replaceAll("_");
    }
}