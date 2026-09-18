package io.myclaw.core.memory;

import io.myclaw.core.message.Message;

import java.util.Collection;
import java.util.List;

/**
 * 对话记忆 —— Agent 的"短期记忆"。
 *
 * <p>只负责保存消息，不负责组装成请求（那是 {@link io.myclaw.core.agent.Agent} 的事）。
 * 实现可以是内存、Redis、数据库，或者任何自定义存储。
 */
public interface Memory {

    /** 当前记忆中的全部消息（快照，不可变）。 */
    List<Message> messages();

    /** 追加一条消息。 */
    void add(Message message);

    /** 批量追加。 */
    default void addAll(Collection<Message> messages) {
        messages.forEach(this::add);
    }

    /** 清空记忆。 */
    void clear();

    /** 当前消息条数。 */
    int size();

    /** 创建一个不做任何保存的空实现，用于一次性、无状态的调用。 */
    static Memory noop() {
        return new Memory() {
            @Override
            public List<Message> messages() {
                return List.of();
            }

            @Override
            public void add(Message message) {
                // 故意丢弃
            }

            @Override
            public void clear() {
            }

            @Override
            public int size() {
                return 0;
            }
        };
    }
}
