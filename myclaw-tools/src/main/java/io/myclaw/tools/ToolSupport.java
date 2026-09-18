package io.myclaw.tools;

import tools.jackson.databind.JsonNode;

/**
 * 内置工具共用的小工具方法：参数读取与文本截断。
 *
 * <p>工具的参数校验错误信息是模型自我纠错的主要依据，所以这里统一保证
 * 「缺哪个参数、期望什么类型」都写清楚，而不是抛出 NPE 让模型猜。
 */
final class ToolSupport {

    private ToolSupport() {
    }

    /**
     * 读取一个必填字符串参数。
     *
     * @param arguments 模型给出的参数对象
     * @param field     参数名
     * @return 去空白后的参数值
     * @throws IllegalArgumentException 参数缺失、为空或不是字符串时
     */
    static String requiredString(JsonNode arguments, String field) {
        JsonNode value = arguments == null ? null : arguments.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("缺少必需参数 `" + field + "`");
        }
        String text = value.asString();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("参数 `" + field + "` 不能为空白");
        }
        return text;
    }

    /**
     * 读取一个可选字符串参数。
     *
     * @param arguments    模型给出的参数对象
     * @param field        参数名
     * @param defaultValue 缺失或为空白时返回的默认值
     */
    static String optionalString(JsonNode arguments, String field, String defaultValue) {
        JsonNode value = arguments == null ? null : arguments.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        String text = value.asString();
        return text == null || text.isBlank() ? defaultValue : text;
    }

    /**
     * 读取一个可选整数参数，缺失时返回默认值。
     *
     * @throws IllegalArgumentException 参数存在但不是整数时
     */
    static int optionalInt(JsonNode arguments, String field, int defaultValue) {
        JsonNode value = arguments == null ? null : arguments.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isNumber() && !value.isString()) {
            throw new IllegalArgumentException("参数 `" + field + "` 必须是整数");
        }
        try {
            return value.isNumber() ? value.asInt() : Integer.parseInt(value.asString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 `" + field + "` 必须是整数，实际为: " + value.asString());
        }
    }

    /**
     * 读取一个可选布尔参数，缺失时返回默认值。
     *
     * @throws IllegalArgumentException 参数存在但不是布尔时
     */
    static boolean optionalBool(JsonNode arguments, String field, boolean defaultValue) {
        JsonNode value = arguments == null ? null : arguments.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            String text = value.asString().trim();
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
        }
        throw new IllegalArgumentException("参数 `" + field + "` 必须是布尔值（true/false）");
    }

    /**
     * 按字符数截断文本，并在末尾注明已截断。
     *
     * @param text      原始文本
     * @param maxLength 最大字符数
     * @return 截断后的文本（未超长时原样返回）
     */
    static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength)
                + "\n...(内容已截断，仅显示前 " + maxLength + " 个字符，原始长度 " + text.length() + " 个字符)";
    }
}
