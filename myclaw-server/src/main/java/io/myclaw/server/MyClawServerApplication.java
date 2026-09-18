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
        SpringApplication.run(MyClawServerApplication.class, args);
    }
}
