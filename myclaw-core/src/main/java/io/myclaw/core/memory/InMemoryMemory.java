package io.myclaw.core.memory;

import io.myclaw.core.message.Message;
import io.myclaw.core.message.Role;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于内存的滑动窗口记忆。
 *
 * <p>当消息条数超过 {@code maxMessages} 时，从最旧的一端丢弃。丢弃时会做一次
 * <b>结构修复</b>：如果窗口头部残留的是 {@link Role#TOOL} 消息（它对应的 assistant
 * tool_calls 消息已被裁掉），会一并丢弃 —— 否则违反 OpenAI 协议，多数厂商会直接返回 400。
 *
 * @see #InMemoryMemory() 无上限
 * @see #InMemoryMemory(int) 指定窗口大小
 */
public final class InMemoryMemory implements Memory {

    private final int maxMessages;
    private final List<Message> messages = new ArrayList<>();

    /** 创建一个不限制长度的记忆。 */
    public InMemoryMemory() {
        this(0);
    }

    /**
     * @param maxMessages 保留的最大消息条数；{@code <= 0} 表示不限制
     */
    public InMemoryMemory(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    @Override
    public synchronized List<Message> messages() {
        return List.copyOf(messages);
    }

    @Override
    public synchronized void add(Message message) {
        messages.add(message);
        trim();
    }

    @Override
    public synchronized void clear() {
        messages.clear();
    }

    @Override
    public synchronized int size() {
        return messages.size();
    }

    /** 当前配置的窗口大小；0 表示不限制。 */
    public int maxMessages() {
        return maxMessages;
    }

    private void trim() {
        if (maxMessages <= 0 || messages.size() <= maxMessages) {
            return;
        }
        int from = messages.size() - maxMessages;
        while (from < messages.size() && messages.get(from).role() == Role.TOOL) {
            from++;
        }
        List<Message> kept = new ArrayList<>(messages.subList(from, messages.size()));
        messages.clear();
        messages.addAll(kept);
    }

    @Override
    public String toString() {
        return "InMemoryMemory(size=" + size() + ", max=" + (maxMessages <= 0 ? "unlimited" : maxMessages) + ")";
    }
}
