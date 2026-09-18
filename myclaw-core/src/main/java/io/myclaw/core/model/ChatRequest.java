package io.myclaw.core.model;

import io.myclaw.core.message.Message;
import io.myclaw.core.tool.ToolDefinition;

import java.util.List;

/**
 * 一次模型调用的完整入参：消息历史 + 可用工具 + 采样参数。
 *
 * @param messages 完整对话消息列表（含 system）
 * @param tools    本次调用暴露给模型的工具定义，空表示不允许调用工具
 * @param options  采样参数
 */
public record ChatRequest(List<Message> messages, List<ToolDefinition> tools, ChatOptions options) {

    public ChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        options = options == null ? ChatOptions.EMPTY : options;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasTools() {
        return !tools.isEmpty();
    }

    public static final class Builder {
        private List<Message> messages = List.of();
        private List<ToolDefinition> tools = List.of();
        private ChatOptions options = ChatOptions.EMPTY;

        public Builder messages(List<Message> messages) {
            this.messages = messages;
            return this;
        }

        public Builder addMessage(Message message) {
            var list = new java.util.ArrayList<>(this.messages);
            list.add(message);
            this.messages = list;
            return this;
        }

        public Builder tools(List<ToolDefinition> tools) {
            this.tools = tools;
            return this;
        }

        public Builder options(ChatOptions options) {
            this.options = options;
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(messages, tools, options);
        }
    }
}
