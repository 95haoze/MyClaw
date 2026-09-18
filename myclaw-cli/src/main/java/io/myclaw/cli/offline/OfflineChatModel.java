package io.myclaw.cli.offline;

import io.myclaw.core.json.Json;
import io.myclaw.core.message.Message;
import io.myclaw.core.message.Role;
import io.myclaw.core.message.ToolCall;
import io.myclaw.core.model.ChatModel;
import io.myclaw.core.model.ChatRequest;
import io.myclaw.core.model.ChatResponse;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一个完全离线的演示模型，不需要任何 API Key 就能跑通完整的 ReAct 循环。
 *
 * <p>行为：
 * <ol>
 *   <li>第一轮：从用户输入里抓一个算术表达式，然后请求调用 {@code calculate} 工具</li>
 *   <li>第二轮：把工具返回的结果组织成自然语言答案</li>
 * </ol>
 *
 * <p>它同时也是"如何自己实现一个 {@link ChatModel}"的最小参考实现。
 */
public final class OfflineChatModel implements ChatModel {

    /** 从自然语言里粗略抓取算术表达式。 */
    private static final Pattern EXPRESSION = Pattern.compile("[0-9][0-9\\s+\\-*/%^().]*[0-9)]");

    private static final String FALLBACK_EXPRESSION = "1234 * 5678";

    @Override
    public ChatResponse chat(ChatRequest request) {
        // 如果记忆里已经出现过工具结果，说明工具阶段结束，该给最终答案了
        String toolResult = request.messages().stream()
                .filter(message -> message.role() == Role.TOOL)
                .reduce((first, second) -> second)
                .map(Message::content)
                .orElse(null);

        if (toolResult != null) {
            return ChatResponse.text("""
                    （离线演示模型）calculate 工具返回的结果是：%s

                    这是用内置的演示模型跑出来的，整条链路是真的：
                    模型请求工具 -> 框架执行工具 -> 结果回填 -> 模型给出答案。
                    配置 myclaw.api-key 之后，换成真实模型即可。"""
                    .formatted(toolResult.strip()));
        }

        String expression = extractExpression(lastUserMessage(request));
        return ChatResponse.of(Message.assistant(
                "我先用 calculate 工具算一下：" + expression,
                List.of(ToolCall.of("calculate", Json.write(Map.of("expression", expression))))));
    }

    @Override
    public String name() {
        return "offline-demo";
    }

    @Override
    public boolean supportsToolCalling() {
        return true;
    }

    private static String lastUserMessage(ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == Role.USER)
                .reduce((first, second) -> second)
                .map(Message::content)
                .orElse("");
    }

    /** 取出输入中最长的算术表达式；找不到时用一个固定表达式兜底，保证演示始终能跑通。 */
    private static String extractExpression(String input) {
        Matcher matcher = EXPRESSION.matcher(input);
        String best = null;
        while (matcher.find()) {
            String candidate = matcher.group().strip();
            if (candidate.length() >= 3 && (best == null || candidate.length() > best.length())) {
                best = candidate;
            }
        }
        if (best == null) {
            return FALLBACK_EXPRESSION;
        }
        // 去掉结尾悬空的运算符，例如 "1+1 等于几" 可能匹配到 "1+1 " 之后的残留
        while (!best.isEmpty() && "+-*/%^(".indexOf(best.charAt(best.length() - 1)) >= 0) {
            best = best.substring(0, best.length() - 1).strip();
        }
        return best.isEmpty() ? FALLBACK_EXPRESSION : best;
    }
}
