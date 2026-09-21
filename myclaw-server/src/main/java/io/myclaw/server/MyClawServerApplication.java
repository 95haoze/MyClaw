package io.myclaw.server;

import org.mybatis.spring.annotation.MapperScan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("io.myclaw.server.persistence.mapper")
@SpringBootApplication
public class MyClawServerApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        // JDK 25 在部分 Windows 环境读取 Shell 特殊目录会失败，强制使用普通文件系统视图。
        System.setProperty("sun.awt.shell.useShellFolder", "false");
        SpringApplication application = new SpringApplication(MyClawServerApplication.class);
        // 本地桌面模式需要 AWT 打开系统文件夹选择器；Spring Boot 默认会强制 headless。
        application.setHeadless(false);
        application.run(args);
    }
}
