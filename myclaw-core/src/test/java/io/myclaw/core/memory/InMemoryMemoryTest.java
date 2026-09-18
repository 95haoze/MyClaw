package io.myclaw.core.memory;

import io.myclaw.core.message.Message;
import io.myclaw.core.message.Role;
import io.myclaw.core.message.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMemoryTest {

    @Test
    @DisplayName("默认不限制长度")
    void unlimitedByDefault() {
        InMemoryMemory memory = new InMemoryMemory();

        for (int i = 1; i <= 50; i++) {
            memory.add(Message.user("消息 " + i));
        }

        assertThat(memory.size()).isEqualTo(50);
    }

    @Test
    @DisplayName("超过窗口大小时丢弃最旧的消息")
    void keepsOnlyLatestMessages() {
        InMemoryMemory memory = new InMemoryMemory(3);

        for (int i = 1; i <= 5; i++) {
            memory.add(Message.user("u" + i));
        }

        assertThat(memory.messages()).extracting(Message::content)
                .containsExactly("u3", "u4", "u5");
    }

    @Test
    @DisplayName("裁剪后窗口头部不会残留孤儿 tool 消息")
    void neverLeavesOrphanToolMessageAtHead() {
        InMemoryMemory memory = new InMemoryMemory(2);
        memory.add(Message.user("u1"));
        memory.add(Message.assistant(null, List.of(ToolCall.of("t", "{}"))));
        memory.add(Message.tool("call-1", "t", "结果一"));
        memory.add(Message.user("u2"));

        // assistant(tool_calls) 与 tool 结果必须成对出现，裁剪时不能只留一半
        assertThat(memory.messages()).singleElement()
                .satisfies(message -> {
                    assertThat(message.role()).isEqualTo(Role.USER);
                    assertThat(message.content()).isEqualTo("u2");
                });
    }

    @Test
    @DisplayName("窗口内完整保留 assistant(tool_calls) + tool 的配对")
    void keepsToolCallPairsTogether() {
        InMemoryMemory memory = new InMemoryMemory(2);
        memory.add(Message.user("u1"));
        memory.add(Message.assistant(null, List.of(ToolCall.of("t", "{}"))));
        memory.add(Message.tool("call-1", "t", "结果一"));

        assertThat(memory.messages()).extracting(Message::role)
                .containsExactly(Role.ASSISTANT, Role.TOOL);
    }

    @Test
    @DisplayName("清空与快照语义")
    void clearAndSnapshotAreSafe() {
        InMemoryMemory memory = new InMemoryMemory();
        memory.add(Message.user("a"));

        List<Message> snapshot = memory.messages();
        memory.add(Message.user("b"));

        assertThat(snapshot).hasSize(1);
        assertThat(memory.size()).isEqualTo(2);

        memory.clear();
        assertThat(memory.messages()).isEmpty();
    }

    @Test
    @DisplayName("noop 记忆丢弃一切")
    void noopMemoryDiscardsEverything() {
        Memory memory = Memory.noop();

        memory.add(Message.user("a"));

        assertThat(memory.size()).isZero();
        assertThat(memory.messages()).isEmpty();
    }
}
