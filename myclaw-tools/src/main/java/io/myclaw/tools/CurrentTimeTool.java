package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 当前时间工具 —— 让模型知道"现在几点"，避免凭空编造日期。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code zone}（string，可选）：IANA 时区 ID，例如 {@code Asia/Shanghai}；默认使用系统时区</li>
 *   <li>{@code pattern}（string，可选）：{@link DateTimeFormatter} 格式，默认 {@code yyyy-MM-dd HH:mm:ss}</li>
 * </ul>
 *
 * <p>返回：格式化后的当前时间、星期几与时区名称。
 * 非法时区或非法格式会抛出带提示的 {@link IllegalArgumentException}，便于模型自我纠正。
 */
public class CurrentTimeTool implements Tool {

    private static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("current_time")
                .description("""
                        获取当前日期时间。可指定 IANA 时区（如 Asia/Shanghai）与日期格式（如 yyyy-MM-dd）。
                        需要知道"今天几号/现在几点"时请调用本工具，不要凭记忆猜测。""")
                .parameters(JsonSchema.object()
                        .string("zone", "IANA 时区 ID，例如 Asia/Shanghai、UTC；默认使用系统时区", false)
                        .string("pattern", "日期时间格式，默认 yyyy-MM-dd HH:mm:ss", false)
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) {
        String zoneText = ToolSupport.optionalString(arguments, "zone", null);
        String pattern = ToolSupport.optionalString(arguments, "pattern", DEFAULT_PATTERN);

        ZoneId zone = resolveZone(zoneText);
        DateTimeFormatter formatter = resolveFormatter(pattern);
        ZonedDateTime now = ZonedDateTime.now(zone);

        String weekday = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        String zoneDisplay = zone.getDisplayName(TextStyle.FULL, Locale.CHINA);
        return "当前时间: " + now.format(formatter) + "\n"
                + "星期: " + weekday + "\n"
                + "时区: " + zone.getId() + "（" + zoneDisplay + "）";
    }

    /** 解析时区，未指定时回退到系统默认时区。 */
    private static ZoneId resolveZone(String zoneText) {
        if (zoneText == null || zoneText.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(zoneText.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "未知时区 `" + zoneText + "`，请使用 IANA 时区 ID，例如 Asia/Shanghai、UTC、America/New_York");
        }
    }

    /** 解析日期格式，给出包含具体错误原因的友好提示。 */
    private static DateTimeFormatter resolveFormatter(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return DateTimeFormatter.ofPattern(DEFAULT_PATTERN);
        }
        try {
            return DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "非法的日期格式 `" + pattern + "`：" + e.getMessage() + "。示例: yyyy-MM-dd HH:mm:ss");
        }
    }
}
