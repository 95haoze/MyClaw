package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.ToolContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CurrentTimeTool} 的单元测试：默认输出、时区与格式、错误处理。
 */
class CurrentTimeToolTest {

    private final CurrentTimeTool tool = new CurrentTimeTool();
    private final ToolContext context = ToolContext.defaults();

    @Test
    @DisplayName("默认返回当前时间、星期几与时区")
    void returnsCurrentTimeByDefault() throws Exception {
        String result = tool.call(Json.obj(), context);

        assertThat(result).contains(String.valueOf(Year.now().getValue()));
        assertThat(result).contains("星期");
        assertThat(result).contains("时区");
        assertThat(result).contains(ZoneId.systemDefault().getId());
    }

    @Test
    @DisplayName("可指定时区")
    void honoursZoneParameter() throws Exception {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");

        String result = tool.call(Json.obj().put("zone", "Asia/Shanghai"), context);

        assertThat(result).contains("Asia/Shanghai");
        assertThat(result).contains(String.valueOf(LocalDate.now(shanghai).getYear()));
        assertThat(result).contains(LocalDate.now(shanghai).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    }

    @Test
    @DisplayName("可指定日期格式")
    void honoursPatternParameter() throws Exception {
        ZoneId utc = ZoneId.of("UTC");
        String expected = LocalDate.now(utc).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        String result = tool.call(Json.obj().put("zone", "UTC").put("pattern", "yyyy/MM/dd"), context);

        assertThat(result).contains(expected);
    }

    @Test
    @DisplayName("非法时区抛出信息友好的异常")
    void rejectsUnknownZone() {
        assertThatThrownBy(() -> tool.call(Json.obj().put("zone", "Mars/Olympus"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知时区")
                .hasMessageContaining("Mars/Olympus");
    }

    @Test
    @DisplayName("非法日期格式抛出信息友好的异常")
    void rejectsIllegalPattern() {
        assertThatThrownBy(() -> tool.call(Json.obj().put("pattern", "yyyy-MM-dd #"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法的日期格式");
    }
}
