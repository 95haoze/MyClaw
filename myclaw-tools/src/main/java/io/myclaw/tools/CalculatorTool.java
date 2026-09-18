package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 计算器工具 —— 让模型完成精确的算术运算（模型自身的心算并不可靠）。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code expression}（string，必填）：算术表达式，例如 {@code (1+2)*3^2}、{@code sqrt(2)}、{@code max(3, 7)}</li>
 * </ul>
 *
 * <p>支持：{@code + - * / % ^}、括号、一元正负号、小数、常量 {@code pi} / {@code e}，
 * 以及函数 {@code sqrt/abs/min/max/pow/round/floor/ceil/log/ln/sin/cos/tan}。
 * 乘方 {@code ^} 右结合，且优先级高于一元负号（{@code -2^2 = -4}）。
 *
 * <p>返回：计算结果的文本形式。整数结果不带小数点（{@code 2+2} 返回 {@code 4}）；
 * 浮点结果保留最多 12 位有效数字并去掉末尾多余的 0。
 *
 * <p>实现说明：解析器是手写的递归下降解析器，<b>不依赖</b>任何脚本引擎或运行时求值，
 * 因此不存在表达式注入风险。
 */
public class CalculatorTool implements Tool {

    private static final int SIGNIFICANT_DIGITS = 12;

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("calculate")
                .description("""
                        计算一个数学表达式并返回结果。支持 + - * / % ^、括号、一元负号、小数、
                        常量 pi 与 e，以及函数 sqrt/abs/min/max/pow/round/floor/ceil/log/ln/sin/cos/tan。
                        涉及精确算术时请使用本工具，不要自己心算。""")
                .parameters(JsonSchema.object()
                        .string("expression", "要计算的表达式，例如 (1+2)*3 或 sqrt(2)+max(3,7)")
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) {
        String expression = ToolSupport.requiredString(arguments, "expression");
        return format(evaluate(expression));
    }

    /**
     * 计算表达式。
     *
     * @param expression 算术表达式
     * @return 计算结果
     * @throws IllegalArgumentException 表达式为空或语法非法
     * @throws ArithmeticException      出现除以零
     */
    public static double evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        return new Parser(expression).parse();
    }

    /**
     * 把计算结果格式化为便于阅读的文本。
     *
     * <p>整数结果不带 {@code .0}；浮点结果保留最多 12 位有效数字并去掉末尾的 0。
     */
    public static String format(double value) {
        if (Double.isNaN(value)) {
            return "NaN（结果不是有效数字）";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity（结果溢出）" : "-Infinity（结果溢出）";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        BigDecimal rounded = BigDecimal.valueOf(value)
                .round(new MathContext(SIGNIFICANT_DIGITS, RoundingMode.HALF_UP))
                .stripTrailingZeros();
        return rounded.toPlainString();
    }

    /**
     * 手写递归下降解析器。
     *
     * <p>文法（优先级由低到高）：
     * <pre>
     * expression := term (('+' | '-') term)*
     * term       := unary (('*' | '/' | '%') unary)*
     * unary      := ('+' | '-') unary | power
     * power      := primary ('^' unary)?          // 右结合
     * primary    := number | constant | func '(' args ')' | '(' expression ')'
     * </pre>
     */
    private static final class Parser {

        private static final List<String> FUNCTIONS = List.of(
                "sqrt", "abs", "min", "max", "pow", "round", "floor", "ceil", "log", "ln", "sin", "cos", "tan");

        private final String source;
        private int pos;

        private Parser(String source) {
            this.source = source;
        }

        /** 解析整个表达式，要求所有字符都被消费。 */
        private double parse() {
            double value = expression();
            skipWhitespace();
            if (pos < source.length()) {
                throw error("存在无法解析的内容 `" + source.charAt(pos) + "`");
            }
            return value;
        }

        private double expression() {
            double left = term();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    left += term();
                } else if (match('-')) {
                    left -= term();
                } else {
                    return left;
                }
            }
        }

        private double term() {
            double left = unary();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    left *= unary();
                } else if (match('/')) {
                    double divisor = unary();
                    if (divisor == 0d) {
                        throw new ArithmeticException("除以零：表达式 `" + source + "` 中出现了除以 0 的运算");
                    }
                    left /= divisor;
                } else if (match('%')) {
                    double divisor = unary();
                    if (divisor == 0d) {
                        throw new ArithmeticException("除以零：表达式 `" + source + "` 中取模运算的除数为 0");
                    }
                    left %= divisor;
                } else {
                    return left;
                }
            }
        }

        private double unary() {
            skipWhitespace();
            if (match('-')) {
                return -unary();
            }
            if (match('+')) {
                return unary();
            }
            return power();
        }

        private double power() {
            double base = primary();
            skipWhitespace();
            if (match('^')) {
                return Math.pow(base, unary());
            }
            return base;
        }

        private double primary() {
            skipWhitespace();
            if (pos >= source.length()) {
                throw error("表达式意外结束，缺少数字、常量或函数调用");
            }
            char current = source.charAt(pos);
            if (current == '(') {
                pos++;
                double value = expression();
                skipWhitespace();
                if (!match(')')) {
                    throw error("缺少与 `(` 配对的右括号 `)`");
                }
                return value;
            }
            if (Character.isDigit(current) || current == '.') {
                return number();
            }
            if (Character.isLetter(current) || current == '_') {
                return identifier();
            }
            throw error("无法识别的字符 `" + current + "`");
        }

        private double number() {
            int start = pos;
            boolean dotSeen = false;
            while (pos < source.length()) {
                char current = source.charAt(pos);
                if (Character.isDigit(current)) {
                    pos++;
                } else if (current == '.' && !dotSeen) {
                    dotSeen = true;
                    pos++;
                } else {
                    break;
                }
            }
            String text = source.substring(start, pos);
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                throw error("无法识别的数字 `" + text + "`");
            }
        }

        private double identifier() {
            int start = pos;
            while (pos < source.length()
                    && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
                pos++;
            }
            String name = source.substring(start, pos).toLowerCase(Locale.ROOT);
            if ("pi".equals(name)) {
                return Math.PI;
            }
            if ("e".equals(name)) {
                return Math.E;
            }
            skipWhitespace();
            if (!match('(')) {
                throw error("未知的标识符 `" + name + "`；常量仅支持 pi / e，函数必须写成 " + name + "(...) 形式");
            }
            List<Double> args = new ArrayList<>();
            skipWhitespace();
            if (match(')')) {
                return apply(name, args);
            }
            args.add(expression());
            skipWhitespace();
            while (match(',')) {
                args.add(expression());
                skipWhitespace();
            }
            if (!match(')')) {
                throw error("函数 `" + name + "` 缺少右括号 `)`");
            }
            return apply(name, args);
        }

        private double apply(String name, List<Double> args) {
            switch (name) {
                case "sqrt": {
                    expectArgs(name, args, 1);
                    double value = args.get(0);
                    if (value < 0) {
                        throw error("sqrt 的参数不能为负数，实际为 " + format(value));
                    }
                    return Math.sqrt(value);
                }
                case "abs": {
                    expectArgs(name, args, 1);
                    return Math.abs(args.get(0));
                }
                case "round": {
                    expectArgs(name, args, 1);
                    return Math.round(args.get(0));
                }
                case "floor": {
                    expectArgs(name, args, 1);
                    return Math.floor(args.get(0));
                }
                case "ceil": {
                    expectArgs(name, args, 1);
                    return Math.ceil(args.get(0));
                }
                case "sin": {
                    expectArgs(name, args, 1);
                    return Math.sin(args.get(0));
                }
                case "cos": {
                    expectArgs(name, args, 1);
                    return Math.cos(args.get(0));
                }
                case "tan": {
                    expectArgs(name, args, 1);
                    return Math.tan(args.get(0));
                }
                case "ln": {
                    expectArgs(name, args, 1);
                    return logarithm(name, args.get(0), Math.E);
                }
                case "log": {
                    if (args.size() == 1) {
                        return logarithm(name, args.get(0), 10d);
                    }
                    expectArgs(name, args, 2);
                    return logarithm(name, args.get(0), args.get(1));
                }
                case "pow": {
                    expectArgs(name, args, 2);
                    return Math.pow(args.get(0), args.get(1));
                }
                case "min": {
                    expectAtLeast(name, args, 1);
                    double result = args.get(0);
                    for (double value : args) {
                        result = Math.min(result, value);
                    }
                    return result;
                }
                case "max": {
                    expectAtLeast(name, args, 1);
                    double result = args.get(0);
                    for (double value : args) {
                        result = Math.max(result, value);
                    }
                    return result;
                }
                default:
                    throw error("未知函数 `" + name + "`，支持的函数有: " + String.join(", ", FUNCTIONS));
            }
        }

        private double logarithm(String name, double value, double base) {
            if (value <= 0) {
                throw error(name + " 的参数必须大于 0，实际为 " + format(value));
            }
            if (base <= 0 || base == 1d) {
                throw error(name + " 的底数必须大于 0 且不等于 1，实际为 " + format(base));
            }
            return Math.log(value) / Math.log(base);
        }

        private void expectArgs(String name, List<Double> args, int expected) {
            if (args.size() != expected) {
                throw error("函数 `" + name + "` 需要 " + expected + " 个参数，实际给了 " + args.size() + " 个");
            }
        }

        private void expectAtLeast(String name, List<Double> args, int minimum) {
            if (args.size() < minimum) {
                throw error("函数 `" + name + "` 至少需要 " + minimum + " 个参数，实际给了 " + args.size() + " 个");
            }
        }

        private void skipWhitespace() {
            while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
                pos++;
            }
        }

        private boolean match(char expected) {
            if (pos < source.length() && source.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(
                    "表达式不合法（位置 " + pos + "）: " + message + "。原始表达式: `" + source + "`");
        }
    }
}
