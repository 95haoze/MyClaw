package io.myclaw.core.skill;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

/** 按名称加载单个技能的完整指令。 */
public final class LoadSkillTool implements Tool {
    private final SkillRegistry registry;

    public LoadSkillTool(SkillRegistry registry) { this.registry = registry; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("load_skill")
                .description("加载一个技能的完整操作指令。在使用某项技能前调用，并遵循返回的指令。")
                .parameters(JsonSchema.object().string("name", "要加载的技能名").build()).build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) {
        String name = Json.text(arguments, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name 不能为空");
        Skill skill = registry.find(name.strip()).orElseThrow(
                () -> new IllegalArgumentException("技能 `" + name + "` 不存在。可用技能: " + registry.names()));
        return skill.instructions();
    }
}
