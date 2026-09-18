package io.myclaw.cli;

import io.myclaw.core.message.ToolCall;
import io.myclaw.core.skill.Skill;
import io.myclaw.core.skill.SkillRegistry;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolRegistry;
import io.myclaw.core.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：验证随仓库交付的 {@code skills/} 目录能被真实扫描、注册，并且只按需展开正文。
 *
 * <p>技能目录按进程工作目录解析，而 Surefire 的工作目录取决于是不是 fork：
 * fork 时是模块目录（{@code myclaw-cli}），{@code -DforkCount=0} 时是 Maven 的启动目录
 * （仓库根目录）。这里同时给出两个候选路径，让两种跑法都能扫到仓库根目录下的 {@code skills/}。
 *
 * <p>使用 {@code offline} profile，不联网、不需要 API Key。
 */
@SpringBootTest(properties = {
        "myclaw.example.cli.enabled=false",
        "myclaw.skills.directories=../skills,skills"
})
@ActiveProfiles("offline")
class MyClawSkillIntegrationTest {

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    @DisplayName("仓库自带的省 token 技能被目录扫描注册，且 frontmatter 已被剥离")
    void scansBundledTokenBudgetSkill() {
        Skill skill = skillRegistry.find("token-budget").orElseThrow();

        assertThat(skill.description()).contains("token");
        assertThat(skill.instructions())
                .startsWith("# 省 Token 协议")
                .doesNotContain("description:")
                .contains("先看目录再读文件");
    }

    @Test
    @DisplayName("技能启用后自动注册 list_skills 与 load_skill")
    void registersSkillTools() {
        assertThat(toolRegistry.names()).contains("list_skills", "load_skill");
    }

    @Test
    @DisplayName("list_skills 只回摘要：既不含技能正文，体积也远小于正文")
    void listSkillsExposesSummaryOnly() {
        ToolResult result = toolRegistry.execute(ToolCall.of("list_skills", "{}"), ToolContext.defaults());
        String body = skillRegistry.find("token-budget").orElseThrow().instructions();

        assertThat(result.error()).isFalse();
        assertThat(result.content()).contains("token-budget");
        assertThat(result.content().length()).isLessThan(body.length());
    }

    @Test
    @DisplayName("load_skill 才返回完整正文，这正是按需加载省 token 的关键")
    void loadSkillReturnsBodyOnDemand() {
        ToolResult result = toolRegistry.execute(
                ToolCall.of("load_skill", "{\"name\":\"token-budget\"}"), ToolContext.defaults());

        assertThat(result.error()).isFalse();
        assertThat(result.content())
                .startsWith("# 省 Token 协议")
                .contains("自检");
    }

    @Test
    @DisplayName("加载不存在的技能会给出可用技能名，便于模型自我纠正")
    void reportsUnknownSkill() {
        ToolResult result = toolRegistry.execute(
                ToolCall.of("load_skill", "{\"name\":\"missing\"}"), ToolContext.defaults());

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("missing").contains("token-budget");
    }
}
