package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.ToolContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CalculatorTool} 的单元测试：四则运算、优先级、括号、幂、函数、错误处理与结果格式化。
 */
class CalculatorToolTest {

    private final CalculatorTool tool = new CalculatorTool();
    private final ToolContext context = ToolContext.defaults();

    private String calculate(String expression) throws Exception {
        return tool.call(Json.obj().put("expression", expression), context);
    }

    @Test
    @DisplayName("工具定义正确")
    void exposesDefinition() {
        assertThat(tool.name()).isEqualTo("calculate");
        assertThat(tool.definition().description()).isNotBlank();
        assertThat(tool.definition().parameters().path("properties").has("expression")).isTrue();
        assertThat(tool.definition().parameters().path("required").get(0).asString()).isEqualTo("expression");
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "2+2, 4",
            "2+3*4, 14",
            "(2+3)*4, 20",
            "10/4, 2.5",
            "10%3, 1",
            "3--2, 5",
            "-(2+3), -5",
            "1.5+0.5, 2",
            "0.1+0.2, 0.3",
            "2^10, 1024",
            "2^3^2, 512",
            "-2^2, -4",
            "1 + 2 * 3 - 4 / 2, 5"
    })
    @DisplayName("四则运算、优先级、括号与幂")
    void evaluatesArithmetic(String expression, String expected) throws Exception {
        assertThat(calculate(expression)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(delimiter = ';', value = {
            "sqrt(16); 4",
            "sqrt(2); 1.41421356237",
            "abs(-5); 5",
            "max(3,7); 7",
            "min(2,3,4); 2",
            "pow(2,10); 1024",
            "round(2.6); 3",
            "round(2.4); 2",
            "floor(1.8); 1",
            "ceil(1.2); 2",
            "log(100); 2",
            "log(8,2); 3",
            "ln(e); 1",
            "sin(0); 0",
            "cos(0); 1",
            "tan(0); 0"
    })
    @DisplayName("内置函数")
    void evaluatesFunctions(String expression, String expected) throws Exception {
        assertThat(calculate(expression)).isEqualTo(expected);
    }

    @Test
    @DisplayName("支持常量 pi 与 e")
    void supportsConstants() throws Exception {
        assertThat(calculate("pi")).startsWith("3.1415926535");
        assertThat(calculate("e")).startsWith("2.718281828");
        assertThat(calculate("2*pi")).startsWith("6.283185307");
    }

    @Test
    @DisplayName("整数结果不带 .0")
    void formatsIntegralResultsWithoutDecimalPoint() throws Exception {
        assertThat(calculate("6/3")).isEqualTo("2").doesNotContain(".");
        assertThat(calculate("2^10")).isEqualTo("1024");
        assertThat(calculate("sqrt(9)")).isEqualTo("3");
    }

    @Test
    @DisplayName("除以零抛 ArithmeticException")
    void rejectsDivisionByZero() {
        assertThatThrownBy(() -> calculate("1/0"))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("除以零");
        assertThatThrownBy(() -> calculate("5%(3-3)"))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("除以零");
    }

    @ParameterizedTest(name = "非法表达式: {0}")
    @ValueSource(strings = {
            "2+*3",
            "(1+2",
            "1+2)",
            "foo(1)",
            "sqrt(1,2)",
            "sqrt(-1)",
            "2 & 3",
            ""
    })
    @DisplayName("非法表达式抛 IllegalArgumentException 且信息可读")
    void rejectsInvalidExpressions(String expression) {
        assertThatThrownBy(() -> calculate(expression))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("null");
    }

    @Test
    @DisplayName("非法表达式的错误信息包含原始表达式，便于模型自我纠正")
    void errorMessageExplainsTheProblem() {
        assertThatThrownBy(() -> calculate("2+*3")).hasMessageContaining("2+*3");
        assertThatThrownBy(() -> calculate("foo(1)")).hasMessageContaining("未知函数");
        assertThatThrownBy(() -> calculate("(1+2")).hasMessageContaining("右括号");
    }

    @Test
    @DisplayName("缺少 expression 参数时给出明确错误")
    void requiresExpression() {
        assertThatThrownBy(() -> tool.call(Json.obj(), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expression");
    }
}
