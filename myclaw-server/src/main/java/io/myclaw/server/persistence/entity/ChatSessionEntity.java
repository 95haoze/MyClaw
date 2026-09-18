package io.myclaw.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.Instant;

@TableName("chat_session")
public class ChatSessionEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("title")
    private String title;

    @TableField("status")
    private String status;

    @TableField("owner_email")
    private String ownerEmail;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    protected ChatSessionEntity() {
    }

    public ChatSessionEntity(String id, String title) {
        this(id, title, null);
    }

    public ChatSessionEntity(String id, String title, String ownerEmail) {
        this.id = id;
        this.title = title;
        this.ownerEmail = ownerEmail;
        this.status = "ACTIVE";
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    public void rename(String title) {
        this.title = title;
        touch();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
