package io.myclaw.server.audit;

import io.myclaw.server.annotation.RepeatSubmit;
import io.myclaw.server.error.RepeatSubmitException;
import io.myclaw.server.utils.RedisLockUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Arrays;

@Aspect
@Component
public class RepeatSubmitAspect {
    private static final Logger log = LoggerFactory.getLogger(RepeatSubmitAspect.class);

    private final RedisLockUtil lockUtil;
    private final ObjectMapper objectMapper;

    public RepeatSubmitAspect(RedisLockUtil lockUtil, ObjectMapper objectMapper) {
        this.lockUtil = lockUtil;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(repeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) throws Throwable {
        if (repeatSubmit.interval() <= 0) {
            throw new IllegalArgumentException("RepeatSubmit interval must be greater than zero");
        }
        String key = buildLockKey(joinPoint, repeatSubmit.checkParams());
        if (!lockUtil.tryLock(key, repeatSubmit.interval())) {
            log.warn("拦截重复提交 操作={} 防重键={}", joinPoint.getSignature().toShortString(), key);
            throw new RepeatSubmitException(repeatSubmit.message());
        }
        // Do not unlock here: the TTL is the debounce window, including after a fast successful response.
        return joinPoint.proceed();
    }

    String buildLockKey(ProceedingJoinPoint joinPoint, boolean checkParams) {
        StringBuilder key = new StringBuilder("myclaw:repeat-submit:")
                .append(currentUser()).append(':')
                .append(requestMethod()).append(':')
                .append(requestUri());
        if (checkParams) key.append(':').append(parameterFingerprint(joinPoint.getArgs()));
        return key.toString();
    }

    private String parameterFingerprint(Object[] arguments) {
        Object[] serializable = Arrays.stream(arguments == null ? new Object[0] : arguments)
                .filter(argument -> !(argument instanceof ServletRequest))
                .filter(argument -> !(argument instanceof ServletResponse))
                .filter(argument -> !(argument instanceof InputStream))
                .filter(argument -> !(argument instanceof OutputStream))
                .toArray();
        try {
            byte[] json = objectMapper.writeValueAsBytes(serializable);
            return sha256(json);
        } catch (Exception exception) {
            String fallback = Arrays.deepToString(serializable);
            return sha256(fallback.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return "anonymous";
        return authentication.getName();
    }

    private static String requestMethod() {
        ServletRequestAttributes attributes = requestAttributes();
        return attributes == null ? "METHOD" : attributes.getRequest().getMethod();
    }

    private static String requestUri() {
        ServletRequestAttributes attributes = requestAttributes();
        return attributes == null ? "non-http" : attributes.getRequest().getRequestURI();
    }

    private static ServletRequestAttributes requestAttributes() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes : null;
    }
}