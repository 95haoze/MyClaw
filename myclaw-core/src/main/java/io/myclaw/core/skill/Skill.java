package io.myclaw.core.skill;

/**
 * 可按需加载的技能指令。技能只把名称和简短描述暴露给模型，完整指令仅在模型调用
 * {@code load_skill} 时进入上下文。
 */
public interface Skill {

    String name();

    String description();

    String instructions();

    default boolean enabled() {
        return true;
    }

    static Skill of(String name, String description, String instructions) {
        return new SimpleSkill(name, description, instructions);
    }
}
