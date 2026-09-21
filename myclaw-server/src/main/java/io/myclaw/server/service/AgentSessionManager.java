package io.myclaw.server.service;

import io.myclaw.core.agent.Agent;
import io.myclaw.server.session.AgentSession;
import io.myclaw.spring.factory.AgentFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Service
public class AgentSessionManager {

    private final AgentFactory agentFactory;
    private final ChatHistoryService chatHistoryService;
    private final Duration sessionTimeout;
    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();

    public AgentSessionManager(
            AgentFactory agentFactory,
            ChatHistoryService chatHistoryService,
            @Value("${myclaw.server.session.timeout:PT30M}") Duration sessionTimeout
    ) {
        this.agentFactory = agentFactory;
        this.chatHistoryService = chatHistoryService;
        this.sessionTimeout = sessionTimeout;
    }

    public <T> T execute(String sessionId, Function<Agent, T> action) {
        return get(sessionId, null, "workspace-write").execute(action);
    }

    public <T> T execute(String sessionId, java.nio.file.Path workingDirectory, Function<Agent, T> action) {
        return get(sessionId, workingDirectory, "workspace-write").execute(action);
    }

    public <T> T execute(String sessionId, java.nio.file.Path workingDirectory, String permissionMode, Function<Agent, T> action) {
        return get(sessionId, workingDirectory, normalizePermission(permissionMode)).execute(action);
    }
    public boolean remove(String sessionId) {
        String normalizedId = normalizeSessionId(sessionId);
        AgentSession session = sessions.get(normalizedId);
        if (session == null) {
            return false;
        }
        return session.execute(agent -> sessions.remove(normalizedId, session));
    }

    int sessionCount() {
        return sessions.size();
    }

    @Scheduled(fixedDelayString = "${myclaw.server.session.cleanup-interval:PT10M}")
    public void removeExpiredSessions() {
        long expireBefore = System.currentTimeMillis() - sessionTimeout.toMillis();

        sessions.forEach((sessionId, session) -> {
            if (!session.tryLock()) {
                return;
            }
            try {
                if (session.lastAccessTime() < expireBefore) {
                    sessions.remove(sessionId, session);
                }
            } finally {
                session.unlock();
            }
        });
    }

    private AgentSession get(String sessionId, java.nio.file.Path workingDirectory, String permissionMode) {
        String normalizedId = normalizeSessionId(sessionId);
        return sessions.compute(normalizedId, (id, existing) -> {
            if (existing != null && (workingDirectory == null || existing.workingDirectory().equals(workingDirectory)) && existing.permissionMode().equals(permissionMode)) return existing;
            Agent agent = workingDirectory == null
                    ? agentFactory.create("myclaw-" + id, chatHistoryService.loadRecentMessages(id))
                    : agentFactory.create("myclaw-" + id, chatHistoryService.loadRecentMessages(id), workingDirectory, permissionMode);
            return new AgentSession(agent, permissionMode);
        });
    }

    private static String normalizePermission(String value) {
        return switch (value == null ? "workspace-write" : value) {
            case "read-only", "workspace-write", "full-access" -> value == null ? "workspace-write" : value;
            default -> "workspace-write";
        };
    }
    private static String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        return sessionId.strip();
    }
}
