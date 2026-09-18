package io.myclaw.server.persistence.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("chat_message_feedback")
public class MessageFeedbackEntity {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("message_id") private Long messageId;
    @TableField("owner_email") private String ownerEmail;
    @TableField("`value`") private String value;
    @TableField("updated_at") private Instant updatedAt;
    protected MessageFeedbackEntity(){}
    public MessageFeedbackEntity(Long messageId,String ownerEmail,String value){
        this.messageId=messageId;this.ownerEmail=ownerEmail;this.value=value;this.updatedAt=Instant.now();
    }
    public void setValue(String value){this.value=value;this.updatedAt=Instant.now();}
    public Long getId(){return id;} public Long getMessageId(){return messageId;}
    public String getOwnerEmail(){return ownerEmail;} public String getValue(){return value;}
}
