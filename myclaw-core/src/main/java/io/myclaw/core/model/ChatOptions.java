package io.myclaw.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次模型调用的采样参数。
 *
 * <p>所有字段均可为 {@code null}，表示"沿用 provider 默认值"。{@link #merge(ChatOptions)}
 * 用于把 Agent 级别的默认参数与单次请求的参数合二为一。
 *
 * @param model       覆盖默认模型名
 * @param temperature 采样温度
 * @param topP        核采样
 * @param maxTokens   最大输出 token 数
 * @param stop        停止词
 * @param toolChoice  工具选择策略：{@code auto} / {@code none} / {@code required}
 * @param extra       透传给 provider 的额外字段，用于承接厂商私有参数
 */
public record ChatOptions(
        String model,
        Double temperature,
        Double topP,
        Integer maxTokens,
        List<String> stop,
        String toolChoice,
        Map<String, Object> extra) {

    public static final ChatOptions EMPTY = builder().build();

    public ChatOptions {
        stop = stop == null ? List.of() : List.copyOf(stop);
        extra = extra == null ? Map.of() : Map.copyOf(extra);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 以当前对象为默认值，用 {@code override} 中非 null 的字段覆盖。 */
    public ChatOptions merge(ChatOptions override) {
        if (override == null || override == EMPTY) {
            return this;
        }
        Map<String, Object> mergedExtra = new LinkedHashMap<>(extra);
        mergedExtra.putAll(override.extra);
        return new ChatOptions(
                override.model != null ? override.model : model,
                override.temperature != null ? override.temperature : temperature,
                override.topP != null ? override.topP : topP,
                override.maxTokens != null ? override.maxTokens : maxTokens,
                override.stop.isEmpty() ? stop : override.stop,
                override.toolChoice != null ? override.toolChoice : toolChoice,
                mergedExtra);
    }

    public static final class Builder {
        private String model;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private final List<String> stop = new ArrayList<>();
        private String toolChoice;
        private final Map<String, Object> extra = new LinkedHashMap<>();

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder stop(String... stopWords) {
            this.stop.addAll(List.of(stopWords));
            return this;
        }

        public Builder toolChoice(String toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder extra(String key, Object value) {
            this.extra.put(key, value);
            return this;
        }

        public ChatOptions build() {
            return new ChatOptions(model, temperature, topP, maxTokens, stop, toolChoice, extra);
        }
    }
}
