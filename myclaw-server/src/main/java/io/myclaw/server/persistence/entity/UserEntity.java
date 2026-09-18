package io.myclaw.server.persistence.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("app_user")
public class UserEntity {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("email") private String email;
    @TableField("display_name") private String displayName;
    @TableField("password_hash") private String passwordHash;
    @TableField("created_at") private Instant createdAt;
    protected UserEntity() {}
    public UserEntity(String email, String displayName, String passwordHash) {
        this.email=email; this.displayName=displayName; this.passwordHash=passwordHash; this.createdAt=Instant.now();
    }
    public Long getId(){return id;} public String getEmail(){return email;} public String getDisplayName(){return displayName;}
    public String getPasswordHash(){return passwordHash;} public Instant getCreatedAt(){return createdAt;}
}
