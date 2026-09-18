package io.myclaw.server.persistence.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("chat_attachment")
public class AttachmentEntity {
    @TableId(type = IdType.INPUT) private String id;
    @TableField("owner_email") private String ownerEmail;
    @TableField("session_id") private String sessionId;
    @TableField("message_id") private Long messageId;
    @TableField("original_name") private String originalName;
    @TableField("content_type") private String contentType;
    @TableField("size_bytes") private long sizeBytes;
    @TableField("storage_path") private String storagePath;
    @TableField("extracted_text") private String extractedText;
    @TableField("created_at") private Instant createdAt;
    protected AttachmentEntity(){}
    public AttachmentEntity(String id,String ownerEmail,String sessionId,String originalName,String contentType,long sizeBytes,String storagePath,String extractedText){
        this.id=id;this.ownerEmail=ownerEmail;this.sessionId=sessionId;this.originalName=originalName;this.contentType=contentType;
        this.sizeBytes=sizeBytes;this.storagePath=storagePath;this.extractedText=extractedText;this.createdAt=Instant.now();
    }
    public String getId(){return id;} public String getOwnerEmail(){return ownerEmail;} public String getSessionId(){return sessionId;}
    public Long getMessageId(){return messageId;} public void linkToMessage(Long messageId){this.messageId=messageId;}
    public String getOriginalName(){return originalName;} public String getContentType(){return contentType;}
    public long getSizeBytes(){return sizeBytes;} public String getStoragePath(){return storagePath;}
    public String getExtractedText(){return extractedText;} public Instant getCreatedAt(){return createdAt;}
}
