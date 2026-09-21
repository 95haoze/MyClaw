package io.myclaw.server.session;

import io.myclaw.core.agent.Agent;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * 一个会话独占的 Agent、锁和最后访问时间。
 */
public final class AgentSession {

    private final Agent agent;
    private final String permissionMode;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile long lastAccessTime;

    public AgentSession(Agent agent) {
        this(agent, "workspace-write");
    }

    public AgentSession(Agent agent, String permissionMode) {
        this.agent = agent;
        this.permissionMode = permissionMode;
        this.lastAccessTime = System.currentTimeMillis();
    }

    public <T> T execute(Function<Agent, T> action) {
        lock.lock();
        try {
            touch();
            return action.apply(agent);
        } finally {
            touch();
            lock.unlock();
        }
    }

    public boolean tryLock() {
        return lock.tryLock();
    }

    public void unlock() {
        lock.unlock();
    }

    public java.nio.file.Path workingDirectory() {
        return agent.toolContext().workingDirectory();
    }

    public String permissionMode() {
        return permissionMode;
    }

    public long lastAccessTime() {
        return lastAccessTime;
    }

    public void touch() {
        lastAccessTime = System.currentTimeMillis();
    }
}
