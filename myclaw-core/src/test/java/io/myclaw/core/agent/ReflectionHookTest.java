package io.myclaw.core.agent;

import io.myclaw.core.exception.MaxIterationsException;
import io.myclaw.core.memory.InMemoryReflectionStore;
import io.myclaw.core.memory.Reflection;
import io.myclaw.core.support.ScriptedChatModel;
import io.myclaw.core.support.TestTools;
import io.myclaw.core.tool.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ReflectionHook} 的复盘行为测试 —— 离线、确定性，不依赖真实模型。
 */
class ReflectionHookTest {

    @Test
    @DisplayName("任务正常完成时沉淀一条 SUCCESS 经验")
    void recordsSuccessReflection() {
        ScriptedChatModel model = ScriptedChatModel.create()
                .thenCallTool("add", "{\"a\":2,\"b\":3}")
                .thenReply("2 + 3 = 5");
        ToolRegistry registry = new ToolRegistry().register(TestTools.add());
        InMemoryReflectionStore store = new InMemoryReflectionStore();
        Agent agent = Agent.builder("r")
                .model(model)
                .tools(registry)
                .hook(new ReflectionHook(store))
                .build();

        AgentResponse response = agent.run("帮我算一下 2+3");

        assertThat(response.content()).isEqualTo("2 + 3 = 5");
        assertThat(store.size()).isEqualTo(1);
        Reflection reflection = store.all().getFirst();
        assertThat(reflection.outcome()).isEqualTo(Reflection.Outcome.SUCCESS);
        assertThat(reflection.toolsUsed()).containsExactly("add");
        assertThat(reflection.failedTools()).isEmpty();
        assertThat(reflection.task()).isEqualTo("帮我算一下 2+3");
        assertThat(reflection.iterations()).isEqualTo(2);
        assertThat(reflection.lesson()).contains("add");
    }

    @Test
    @DisplayName("工具失败但最终完成时沉淀 PARTIAL 经验")
    void recordsPartialReflectionWhenToolFails() {
        ScriptedChatModel model = ScriptedChatModel.create()
                .thenCallTool("explode", "{}")
                .thenReply("工具挂了，我直接回答。");
        ToolRegistry registry = new ToolRegistry().register(TestTools.exploding());
        InMemoryReflectionStore store = new InMemoryReflectionStore();
        Agent agent = Agent.builder("r")
                .model(model)
                .tools(registry)
                .hook(new ReflectionHook(store))
                .build();

        AgentResponse response = agent.run("go");

        assertThat(response.content()).isEqualTo("工具挂了，我直接回答。");
        assertThat(store.size()).isEqualTo(1);
        Reflection reflection = store.all().getFirst();
        assertThat(reflection.outcome()).isEqualTo(Reflection.Outcome.PARTIAL);
        assertThat(reflection.failedTools()).containsExactly("explode");
        assertThat(reflection.toolsUsed()).containsExactly("explode");
    }

    @Test
    @DisplayName("执行抛异常时沉淀 FAILURE 经验")
    void recordsFailureReflectionOnError() {
        ScriptedChatModel model = ScriptedChatModel.create();
        for (int i = 0; i < 10; i++) {
            model.thenCallTool("add", "{\"a\":1,\"b\":1}");
        }
        ToolRegistry registry = new ToolRegistry().register(TestTools.add());
        InMemoryReflectionStore store = new InMemoryReflectionStore();
        Agent agent = Agent.builder("loop")
                .model(model)
                .tools(registry)
                .hook(new ReflectionHook(store))
                .maxIterations(3)
                .build();

        assertThatThrownBy(() -> agent.run("陷入循环"))
                .isInstanceOf(MaxIterationsException.class);

        assertThat(store.size()).isEqualTo(1);
        Reflection reflection = store.all().getFirst();
        assertThat(reflection.outcome()).isEqualTo(Reflection.Outcome.FAILURE);
        assertThat(reflection.task()).isEqualTo("陷入循环");
        assertThat(reflection.lesson()).contains("执行失败");
    }

    @Test
    @DisplayName("复盘逻辑异常不会影响 Agent 主流程")
    void brokenGeneratorDoesNotBreakAgent() {
        ScriptedChatModel model = ScriptedChatModel.create().thenReply("正常回答");
        Agent agent = Agent.builder("t")
                .model(model)
                .hook(new ReflectionHook(new InMemoryReflectionStore(),
                        (a, resp, err) -> { throw new IllegalStateException("复盘炸了"); }))
                .build();

        assertThat(agent.run("hi").content()).isEqualTo("正常回答");
    }

    @Test
    @DisplayName("recall 与 augmentSystemPrompt 能检索并注入历史经验")
    void recallAndAugmentWorkTogether() {
        InMemoryReflectionStore store = new InMemoryReflectionStore();
        store.save(new Reflection("SQL 优化", Reflection.Outcome.SUCCESS,
                List.of("db_query"), List.of(), "任务「SQL 优化」成功完成，使用工具 [db_query]", 2, 100, null));
        store.save(new Reflection("写诗", Reflection.Outcome.SUCCESS,
                List.of(), List.of(), "任务「写诗」成功完成（未使用工具）", 1, 50, null));

        ReflectionHook hook = new ReflectionHook(store);
        List<Reflection> hits = hook.recall("SQL", 5);
        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().task()).isEqualTo("SQL 优化");

        String prompt = ReflectionHook.augmentSystemPrompt("你是助手", hits);
        assertThat(prompt).startsWith("你是助手");
        assertThat(prompt).contains("历史经验").contains("SQL 优化");

        // 无经验时原样返回
        assertThat(ReflectionHook.augmentSystemPrompt("你是助手", List.of())).isEqualTo("你是助手");
    }
}
