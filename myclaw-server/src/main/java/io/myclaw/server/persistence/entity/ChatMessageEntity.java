package io.myclaw.server.persistence.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("chat_message")
public class ChatMessageEntity {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("request_id") private String requestId;
    @TableField("session_id") private String sessionId;
    @TableField("role") private String role;
    @TableField("content") private String content;
    @TableField("status") private String status;
    @TableField("iterations") private Integer iterations;
    @TableField("tool_calls") private Integer toolCalls;
    @TableField("total_tokens") private Integer totalTokens;
    @TableField("duration_millis") private Long durationMillis;
    @TableField("created_at") private Instant createdAt;
    protected ChatMessageEntity() {}
    public ChatMessageEntity(String requestId,String sessionId,String role,String content,String status){
        this.requestId=requestId;this.sessionId=sessionId;this.role=role;this.content=content;this.status=status;this.createdAt=Instant.now();
    }
    public void markStatus(String status){this.status=status;}
    public void setStatistics(int iterations,int toolCalls,int totalTokens,long durationMillis){
        this.iterations=iterations;this.toolCalls=toolCalls;this.totalTokens=totalTokens;this.durationMillis=durationMillis;
    }
    public Long getId(){return id;} public String getRequestId(){return requestId;} public String getSessionId(){return sessionId;}
    public String getRole(){return role;} public String getContent(){return content;} public String getStatus(){return status;}
    public Integer getIterations(){return iterations;} public Integer getToolCalls(){return toolCalls;}
    public Integer getTotalTokens(){return totalTokens;} public Long getDurationMillis(){return durationMillis;}
    public Instant getCreatedAt(){return createdAt;}
}
