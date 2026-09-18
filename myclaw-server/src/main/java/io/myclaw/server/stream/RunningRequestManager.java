package io.myclaw.server.stream;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class RunningRequestManager {

    private final ConcurrentHashMap<String, RunningRequest> requests = new ConcurrentHashMap<>();

    public RunningRequest start(String requestId) {
        String normalizedId = normalize(requestId);
        RunningRequest request = new RunningRequest();
        if (requests.putIfAbsent(normalizedId, request) != null) {
            throw new IllegalArgumentException("requestId 正在执行或重复使用");
        }
        return request;
    }

    public boolean cancel(String requestId) {
        RunningRequest request = requests.remove(normalize(requestId));
        return request != null && request.cancel();
    }

    public void complete(String requestId, RunningRequest request) {
        requests.remove(requestId, request);
    }

    private static String normalize(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId 不能为空");
        }
        String normalized = requestId.strip();
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("requestId 长度不能超过 64");
        }
        return normalized;
    }
}
