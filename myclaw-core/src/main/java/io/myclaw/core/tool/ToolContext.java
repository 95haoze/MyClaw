package io.myclaw.core.tool;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 单次工具调用的上下文 —— 工具与"运行时环境"之间的唯一通道。
 *
 * <p>其中最重要的能力是 {@link #resolve(String)}：所有涉及文件系统的工具都应通过它来解析路径，
 * 这样框架可以统一保证「Agent 只能访问工作目录内的文件」这条安全边界。
 */
public final class ToolContext {

    private final String agentName;
    private final Path workingDirectory;
    private final Map<String, Object> attributes;

    private ToolContext(Builder builder) {
        this.agentName = builder.agentName;
        this.workingDirectory = builder.workingDirectory.toAbsolutePath().normalize();
        this.attributes = Map.copyOf(builder.attributes);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 以当前工作目录创建一个最小上下文，适合脚本化/测试场景。 */
    public static ToolContext of(Path workingDirectory) {
        return builder().workingDirectory(workingDirectory).build();
    }

    /** 以 JVM 当前目录创建一个最小上下文。 */
    public static ToolContext defaults() {
        return of(Path.of("."));
    }

    /** 触发本次调用的 Agent 名称。 */
    public String agentName() {
        return agentName;
    }

    /** Agent 的工作目录，文件类工具的沙箱根。 */
    public Path workingDirectory() {
        return workingDirectory;
    }

    /** 全部自定义属性（只读）。 */
    public Map<String, Object> attributes() {
        return attributes;
    }

    /** 读取一个自定义属性。 */
    public Optional<Object> attribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    /** 按类型读取一个自定义属性，缺失或类型不符时抛异常。 */
    public <T> T require(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value == null) {
            throw new IllegalStateException("ToolContext 缺少必需属性: " + key);
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException(
                    "ToolContext 属性 " + key + " 类型应为 " + type.getSimpleName() + "，实际为 " + value.getClass().getSimpleName());
        }
        return type.cast(value);
    }

    /**
     * 把模型给出的路径解析为绝对路径，并强制约束在工作目录内。
     *
     * @param raw 模型给出的路径，可以是相对路径
     * @return 规范化后的绝对路径
     * @throws SecurityException 路径越出工作目录时
     */
    public Path resolve(String raw) {
        Objects.requireNonNull(raw, "路径不能为空");
        Path candidate = Path.of(raw);
        Path resolved = (candidate.isAbsolute() ? candidate : workingDirectory.resolve(candidate)).normalize();
        if (!resolved.startsWith(workingDirectory)) {
            throw new SecurityException("拒绝访问工作目录之外的路径: " + raw + "（工作目录: " + workingDirectory + "）");
        }
        return resolved;
    }

    /** 返回一个基于本上下文、附加了额外属性的新上下文。 */
    public ToolContext withAttribute(String key, Object value) {
        return toBuilder().attribute(key, value).build();
    }

    public Builder toBuilder() {
        return new Builder()
                .agentName(agentName)
                .workingDirectory(workingDirectory)
                .attributes(attributes);
    }

    public static final class Builder {
        private String agentName = "agent";
        private Path workingDirectory = Path.of(".");
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
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

        public ToolContext build() {
            return new ToolContext(this);
        }
    }

    @Override
    public String toString() {
        return "ToolContext(agent=" + agentName + ", workdir=" + workingDirectory + ")";
    }
}
