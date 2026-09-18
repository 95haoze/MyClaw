package io.myclaw.cli;

import io.myclaw.spring.annotation.MyClawParam;
import io.myclaw.spring.annotation.MyClawTool;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用 {@link MyClawTool} 注解声明自定义工具的示例。
 *
 * <p>只要被 Spring 扫描到，这些方法就会在启动时自动注册成 Agent 可调用的工具，
 * 完全不需要写任何胶水代码。
 */
@Component
public class DemoTools {

    /** 温度单位，用于演示枚举参数如何自动生成 JSON Schema 的 enum 约束。 */
    public enum TemperatureUnit {
        CELSIUS, FAHRENHEIT, KELVIN
    }

    /**
     * 生成指定闭区间内的随机整数。
     */
    @MyClawTool(name = "random_number",
            description = "生成一个指定范围内的随机整数。当用户需要随机数、抽奖、掷骰子、随机挑选时使用。")
    public String randomNumber(
            @MyClawParam(value = "min", description = "最小值（包含）") int min,
            @MyClawParam(value = "max", description = "最大值（包含）") int max) {
        if (min > max) {
            throw new IllegalArgumentException("min 不能大于 max，收到 min=" + min + ", max=" + max);
        }
        return String.valueOf(ThreadLocalRandom.current().nextInt(min, max + 1));
    }

    /**
     * 返回当前运行环境的基本信息。
     */
    @MyClawTool(name = "system_info",
            description = "获取当前程序运行环境的信息：操作系统、CPU 核数、Java 版本、JVM 可用内存。"
                    + "当用户询问运行环境、服务器配置、内存占用时使用。")
    public String systemInfo() {
        Runtime runtime = Runtime.getRuntime();
        long freeMegabytes = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMegabytes = runtime.maxMemory() / (1024 * 1024);
        return """
                操作系统: %s %s
                CPU 核数: %d
                Java 版本: %s
                JVM 内存: 已用 %d MB / 上限 %d MB"""
                .formatted(System.getProperty("os.name"),
                        System.getProperty("os.version"),
                        runtime.availableProcessors(),
                        System.getProperty("java.version"),
                        freeMegabytes,
                        maxMegabytes);
    }

    /**
     * 温度单位换算，演示枚举参数。
     */
    @MyClawTool(name = "convert_temperature",
            description = "在摄氏度、华氏度、开尔文之间换算温度。当用户需要转换温度单位时使用。")
    public String convertTemperature(
            @MyClawParam(value = "value", description = "要转换的温度数值") double value,
            @MyClawParam(value = "from", description = "原始单位") TemperatureUnit from,
            @MyClawParam(value = "to", description = "目标单位") TemperatureUnit to) {
        double celsius = switch (from) {
            case CELSIUS -> value;
            case FAHRENHEIT -> (value - 32) * 5 / 9;
            case KELVIN -> value - 273.15;
        };
        double converted = switch (to) {
            case CELSIUS -> celsius;
            case FAHRENHEIT -> celsius * 9 / 5 + 32;
            case KELVIN -> celsius + 273.15;
        };
        return "%.2f %s = %.2f %s".formatted(value, from, converted, to);
    }

    /** 进程启动时间，演示无参工具。 */
    @MyClawTool(name = "jvm_uptime",
            description = "获取当前 JVM 已经运行的秒数。当用户询问程序运行了多久时使用。")
    public long jvmUptime() {
        return ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
    }
}
