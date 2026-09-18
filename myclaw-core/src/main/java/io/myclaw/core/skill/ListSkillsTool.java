package io.myclaw.core.skill;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** 仅列出技能摘要，避免把所有技能正文预先塞进模型上下文。 */
public final class ListSkillsTool implements Tool {
    private final SkillRegistry registry;

    public ListSkillsTool(SkillRegistry registry) { this.registry = registry; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("list_skills")
                .description("列出可用技能的名称和用途。需要专业操作流程时先调用，再用 load_skill 加载完整指令。")
                .parameters(JsonSchema.object().build()).build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) {
        ArrayNode result = Json.arr();
        for (Skill skill : registry.skills()) {
            ObjectNode item = Json.obj();
            item.put("name", skill.name());
            item.put("description", skill.description());
            result.add(item);
        }
        return Json.write(result);
    }
}
