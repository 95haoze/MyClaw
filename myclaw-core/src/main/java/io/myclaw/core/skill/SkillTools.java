package io.myclaw.core.skill;

import io.myclaw.core.tool.Tool;

import java.util.List;

/** {@code list_skills} 与 {@code load_skill} 两个内置工具的工厂。 */
public final class SkillTools {
    private SkillTools() {
    }

    public static List<Tool> all(SkillRegistry registry) {
        return List.of(list(registry), load(registry));
    }

    public static ListSkillsTool list(SkillRegistry registry) {
        return new ListSkillsTool(registry);
    }

    public static LoadSkillTool load(SkillRegistry registry) {
        return new LoadSkillTool(registry);
    }
}
