package io.myclaw.server.audit;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class OperationAuditAspect {
    private static final Logger log = LoggerFactory.getLogger(OperationAuditAspect.class);
    private static final int MAX_VALUE_LENGTH = 80;

    private static final Pattern SENSITIVE_NAME = Pattern.compile(
            ".*(password|secret|token|key|credential|authorization|cookie|content|message|request|body).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\r\\n\\t]");

    @Around("within(io.myclaw.server.controller..*)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String operation = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        String actor = actor();
        try {
            Object result = joinPoint.proceed();
            if (log.isInfoEnabled() && isImportantOperation()) {
                log.info("操作审计 请求编号={} 操作用户={} 操作={} 执行结果=成功 耗时毫秒={} 参数摘要={}",
                        requestId(), actor, operation, elapsedMillis(startedAt),
                        safeArguments(signature.getParameterNames(), joinPoint.getArgs()));
            }
            return result;
        } catch (Throwable error) {
            if (log.isWarnEnabled()) {
                log.warn("操作审计 请求编号={} 操作用户={} 操作={} 执行结果=失败 异常类型={} 耗时毫秒={} 参数摘要={}",
                        requestId(), actor, operation, error.getClass().getSimpleName(), elapsedMillis(startedAt),
                        safeArguments(signature.getParameterNames(), joinPoint.getArgs()));
            }
            throw error;
        }
    }

    private static boolean isImportantOperation() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return true;
        return switch (attributes.getRequest().getMethod()) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }
    static String safeArguments(String[] names, Object[] values) {
        StringJoiner result = new StringJoiner(",", "[", "]");
        for (int index = 0; index < values.length; index++) {
            String name = names != null && index < names.length ? names[index] : "arg" + index;
            result.add(sanitize(name) + "=" + summarize(name, values[index]));
        }
        return result.toString();
    }

    private static String summarize(String name, Object value) {
        if (SENSITIVE_NAME.matcher(name).matches()) {
            return "<redacted>";
        }
        if (value == null) return "null";
        if (value instanceof ServletRequest || value instanceof ServletResponse) return "<servlet>";
        if (value instanceof MultipartFile file) {
            return "file(name=" + sanitize(file.getOriginalFilename()) + ",type="
                    + sanitize(file.getContentType()) + ",size=" + file.getSize() + ")";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value.getClass().isEnum()) {
            return truncate(sanitize(String.valueOf(value)));
        }
        if (value instanceof Collection<?> collection) return value.getClass().getSimpleName() + "(size=" + collection.size() + ")";
        if (value instanceof Map<?, ?> map) return value.getClass().getSimpleName() + "(size=" + map.size() + ")";
        if (value.getClass().isArray()) return value.getClass().getComponentType().getSimpleName() + "[](size=" + Array.getLength(value) + ")";
        return value.getClass().getSimpleName();
    }

    private static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return "anonymous";
        }
        return truncate(sanitize(authentication.getName()));
    }

    private static String requestId() {
        String value = MDC.get(RequestAuditFilter.REQUEST_ID);
        return value == null ? "-" : value;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return CONTROL_CHARS.matcher(value).replaceAll("_");
    }

    private static String truncate(String value) {
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH) + "...";
    }
}