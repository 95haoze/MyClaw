# MyClaw Server

MyClaw 的 Spring Boot HTTP 服务。聊天会话和消息保存到 MySQL，活跃 Agent 保存在当前服务进程内。

## 数据库准备

先在 MySQL 中创建数据库。Hibernate 会在首次启动时创建 chat_session 和 chat_message 表。

CREATE DATABASE myclaw CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

通过环境变量配置连接，不要把密码提交到仓库：

MYCLAW_DB_URL=jdbc:mysql://localhost:3306/myclaw?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8
MYCLAW_DB_USERNAME=root
MYCLAW_DB_PASSWORD=你的密码

模型密钥使用 MYCLAW_API_KEY 环境变量。

## 启动

mvn -pl myclaw-server -am spring-boot:run

服务默认运行在 http://localhost:19090。

## 接口

- POST /api/chat：发送消息并持久化用户消息、助手回复和调用统计。
- DELETE /api/sessions/{sessionId}：清除内存 Agent，并删除数据库中的该会话和消息。

数据库保存完整的用户和助手消息；Agent 从内存移除或服务重启后，会加载最近 40 条已完成消息恢复上下文。

当前使用 spring.jpa.hibernate.ddl-auto=update，适合本地开发。正式部署阶段应改用 Flyway 管理数据库版本。
