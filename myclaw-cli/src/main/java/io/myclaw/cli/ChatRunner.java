package io.myclaw.cli;

import io.myclaw.core.agent.Agent;
import io.myclaw.core.agent.AgentResponse;
import io.myclaw.core.model.ChatResponse;
import io.myclaw.core.model.StreamListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 命令行入口：交互式对话 / 单次提问。
 *
 * <p>可以用 {@code myclaw.example.cli.enabled=false} 关掉（例如跑集成测试时），
 * 避免它去读标准输入。
 */
@Component
@ConditionalOnProperty(name = "myclaw.example.cli.enabled", havingValue = "true", matchIfMissing = true)
public class ChatRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatRunner.class);

    private final Agent agent;

    public ChatRunner(Agent agent) {
        this.agent = agent;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> prompts = args.getOptionValues("prompt");
        if (prompts != null && !prompts.isEmpty()) {
            ask(prompts.get(0));
            return;
        }
        interactive();
    }

    /** 单次提问后退出，适合脚本化验证。 */
    public void ask(String question) {
        // 模型可能分多轮输出（先调工具、再作答），记录是否已经打印过内容以便正确换行
        AtomicBoolean printedAnything = new AtomicBoolean(false);

        StreamListener listener = new StreamListener() {
            @Override
            public void onStart() {
                if (printedAnything.get()) {
                    System.out.println();
                }
            }

            @Override
            public void onText(String delta) {
                printedAnything.set(true);
                System.out.print(delta);
                System.out.flush();
            }

            @Override
            public void onComplete(ChatResponse response) {
                System.out.println();
            }

            @Override
            public void onError(Throwable error) {
                log.debug("流式输出中断", error);
            }
        };

        try {
            AgentResponse response = agent.run(question, listener);
            System.out.printf("[%d 轮模型调用 | %d 次工具 | %d tokens | %d ms]%n",
                    response.iterations(),
                    response.toolResults().size(),
                    response.usage().totalTokens(),
                    response.durationMillis());
        } catch (RuntimeException e) {
            System.out.println();
            System.out.println("调用失败：" + e.getMessage());
            log.debug("Agent 执行异常", e);
        }
    }

    private void interactive() {
        printBanner();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("你 > ");
            System.out.flush();
            String line;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                System.out.println("读取输入失败：" + e.getMessage());
                return;
            }
            if (line == null) {
                System.out.println();
                System.out.println("标准输入已结束，退出。提示：可以用 --prompt=\"你的问题\" 单次提问。");
                return;
            }
            String input = line.strip();
            if (input.isEmpty()) {
                continue;
            }
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                System.out.println("再见。");
                return;
            }
            if (input.equalsIgnoreCase("/reset")) {
                agent.reset();
                System.out.println("对话记忆已清空。");
                continue;
            }
            ask(input);
        }
    }

    private void printBanner() {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("  MyClaw 示例 Agent");
        System.out.println("------------------------------------------------------------");
        System.out.println("  模型 : " + agent.model().name());
        System.out.println("  工具 : " + agent.tools().names());
        System.out.println("------------------------------------------------------------");
        System.out.println("  直接输入问题回车；/reset 清空记忆；exit 退出");
        System.out.println("============================================================");
    }
}
