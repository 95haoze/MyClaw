package io.myclaw.core.agent;

import io.myclaw.core.model.ChatOptions;
import io.myclaw.core.model.StreamListener;

import java.util.Map;

/**
 * 一次 Agent 调用的入参，可以覆盖 Agent 上的默认配置。
 *
 * @param input        用户输入，必填
 * @param systemPrompt 覆盖 Agent 的默认系统提示词
 * @param options      覆盖默认采样参数
 * @param listener     流式回调；为 {@code null} 时使用非流式调用
 * @param attributes   注入 {@link io.myclaw.core.tool.ToolContext} 的额外属性
 */
public record AgentRequest(
        String input,
        String systemPrompt,
        ChatOptions options,
        StreamListener listener,
        Map<String, Object> attributes) {

    public AgentRequest {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static AgentRequest of(String input) {
        return builder().input(input).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String input;
        private String systemPrompt;
        private ChatOptions options;
        private StreamListener listener;
        private final Map<String, Object> attributes = new java.util.LinkedHashMap<>();

        public Builder input(String input) {
            this.input = input;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder options(ChatOptions options) {
            this.options = options;
            return this;
        }

        public Builder listener(StreamListener listener) {
            this.listener = listener;
            return this;
        }

        /** 开启流式输出，文本增量会实时回调到 {@code listener}。 */
        public Builder streaming(StreamListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public Builder attributes(Map<String, ?> values) {
            this.attributes.putAll(values);
            return this;
        }

        public AgentRequest build() {
            return new AgentRequest(input, systemPrompt, options, listener, attributes);
        }
    }
}
