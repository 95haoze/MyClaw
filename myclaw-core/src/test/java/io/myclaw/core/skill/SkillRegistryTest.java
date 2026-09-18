package io.myclaw.core.skill;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.ToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillRegistryTest {
    @TempDir Path tempDir;

    @Test
    void registersCodeSkillsAndLaterRegistrationWins() {
        SkillRegistry registry = new SkillRegistry()
                .register(Skill.of("review", "first", "one"))
                .register(Skill.of("review", "second", "two"));
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.find("review").orElseThrow().instructions()).isEqualTo("two");
    }

    @Test
    void scansClaudeStyleSkillFiles() throws Exception {
        Path skillDir = Files.createDirectories(tempDir.resolve("pdf-review"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: pdf-review
                description: "Review PDF documents"
                ---
                # Workflow
                Inspect every page.
                """);
        SkillRegistry registry = new SkillRegistry().scan(tempDir);
        Skill skill = registry.find("pdf-review").orElseThrow();
        assertThat(skill.description()).isEqualTo("Review PDF documents");
        assertThat(skill.instructions()).contains("# Workflow").doesNotContain("description:");
    }

    @Test
    void builtInToolsExposeSummaryThenLoadOnlyOneBody() throws Exception {
        SkillRegistry registry = new SkillRegistry()
                .register(Skill.of("alpha", "Alpha description", "Alpha secret instructions"))
                .register(Skill.of("beta", "Beta description", "Beta secret instructions"));
        String listed = new ListSkillsTool(registry).call(Json.obj(), ToolContext.defaults());
        assertThat(listed).contains("alpha", "Alpha description", "beta", "Beta description")
                .doesNotContain("secret instructions");
        String loaded = new LoadSkillTool(registry).call(Json.obj().put("name", "alpha"), ToolContext.defaults());
        assertThat(loaded).isEqualTo("Alpha secret instructions");
        assertThatThrownBy(() -> new LoadSkillTool(registry)
                .call(Json.obj().put("name", "missing"), ToolContext.defaults()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
