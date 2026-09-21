package io.myclaw.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("chat_tool_execution")
public class ChatToolExecutionEntity {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("message_id") private Long messageId;
    @TableField("execution_id") private String executionId;
    @TableField("tool_name") private String toolName;
    @TableField("arguments_json") private String arguments;
    @TableField("status") private String status;
    @TableField("duration_millis") private Long durationMillis;
    @TableField("result_text") private String result;
    @TableField("sequence_no") private Integer sequence;
    @TableField("created_at") private Instant createdAt;

    protected ChatToolExecutionEntity() {}

    public ChatToolExecutionEntity(Long messageId, String executionId, String toolName, String arguments,
                                   String status, long durationMillis, String result, int sequence) {
        this.messageId=messageId; this.executionId=executionId; this.toolName=toolName; this.arguments=arguments;
        this.status=status; this.durationMillis=durationMillis; this.result=result; this.sequence=sequence;
        this.createdAt=Instant.now();
    }

    public Long getId(){return id;} public Long getMessageId(){return messageId;}
    public String getExecutionId(){return executionId;} public String getToolName(){return toolName;}
    public String getArguments(){return arguments;} public String getStatus(){return status;}
    public Long getDurationMillis(){return durationMillis;} public String getResult(){return result;}
    public Integer getSequence(){return sequence;} public Instant getCreatedAt(){return createdAt;}
}
