package io.myclaw.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MyClaw 示例应用入口。
 *
 * <p>三种运行方式：
 * <pre>
 * # 1. 交互式对话（需要 API Key）
 * mvn -pl myclaw-cli spring-boot:run
 *
 * # 2. 单次提问后退出（适合脚本化验证）
 * mvn -pl myclaw-cli spring-boot:run -Dspring-boot.run.arguments="--prompt=杭州现在几点"
 *
 * # 3. 完全离线的演示（不需要 API Key，用内置的演示模型跑通 ReAct 循环）
 * mvn -pl myclaw-cli spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=offline --prompt=算一下 1234 * 5678"
 * </pre>
 */
@SpringBootApplication
public class MyClawCliApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyClawCliApplication.class, args);
    }
}
