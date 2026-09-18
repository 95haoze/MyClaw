package io.myclaw.core.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * 技能注册表，支持代码注册和 Claude 风格的 {@code <skill>/SKILL.md} 目录扫描。
 */
public final class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);
    private static final String SKILL_FILE = "SKILL.md";
    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public SkillRegistry register(Skill skill) {
        if (!skill.enabled()) {
            return this;
        }
        Skill previous = skills.put(skill.name(), skill);
        if (previous != null) {
            log.warn("技能名 {} 重复注册，后注册的实现将生效", skill.name());
        }
        return this;
    }

    public SkillRegistry registerAll(Collection<? extends Skill> values) {
        values.forEach(this::register);
        return this;
    }

    public SkillRegistry registerAll(Skill... values) {
        for (Skill skill : values) register(skill);
        return this;
    }

    /**
     * 递归扫描目录中的 SKILL.md。无效文件会被记录并跳过，不影响其它技能。
     */
    public SkillRegistry scan(Path directory) {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            log.warn("技能扫描目录不存在或不是目录，已跳过: {}", root);
            return this;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> SKILL_FILE.equals(path.getFileName().toString()))
                    .sorted()
                    .forEach(path -> {
                        try {
                            register(parse(path));
                        } catch (RuntimeException | IOException e) {
                            log.warn("无法加载技能文件 {}，已跳过: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("扫描技能目录 {} 失败: {}", root, e.getMessage());
        }
        return this;
    }

    public Optional<Skill> find(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public boolean contains(String name) {
        return skills.containsKey(name);
    }

    public int size() {
        return skills.size();
    }

    public boolean isEmpty() {
        return skills.isEmpty();
    }

    public Set<String> names() {
        return Set.copyOf(skills.keySet());
    }

    public List<Skill> skills() {
        return List.copyOf(skills.values());
    }

    static Skill parse(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (!text.startsWith("---")) {
            throw new IllegalArgumentException("缺少 YAML frontmatter");
        }
        int firstEnd = lineEnd(text, 0);
        int closing = findClosingDelimiter(text, firstEnd);
        if (closing < 0) throw new IllegalArgumentException("YAML frontmatter 未闭合");

        String name = null;
        String description = null;
        for (String line : text.substring(firstEnd, closing).split("\\R")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).strip();
            String value = unquote(line.substring(colon + 1).strip());
            if ("name".equals(key)) name = value;
            if ("description".equals(key)) description = value;
        }
        int bodyStart = lineEnd(text, closing);
        return new SimpleSkill(name, description, text.substring(bodyStart).strip());
    }

    private static int findClosingDelimiter(String text, int from) {
        int cursor = from;
        while (cursor < text.length()) {
            int end = text.indexOf('\n', cursor);
            if (end < 0) end = text.length();
            String line = text.substring(cursor, end).replace("\r", "").strip();
            if ("---".equals(line)) return cursor;
            cursor = end + 1;
        }
        return -1;
    }

    private static int lineEnd(String text, int start) {
        int newline = text.indexOf('\n', start);
        return newline < 0 ? text.length() : newline + 1;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
