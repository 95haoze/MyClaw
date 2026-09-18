package io.myclaw.core.skill;

import java.util.Objects;

/** 不依赖框架的不可变技能实现，适合代码直接注册。 */
public record SimpleSkill(String name, String description, String instructions) implements Skill {

    public SimpleSkill {
        name = requireText(name, "技能名");
        description = requireText(description, "技能描述");
        instructions = requireText(instructions, "技能指令");
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + "不能为空");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return normalized;
    }
}
