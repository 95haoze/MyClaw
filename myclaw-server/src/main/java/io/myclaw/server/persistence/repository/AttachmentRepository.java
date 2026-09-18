package io.myclaw.server.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.myclaw.server.persistence.entity.AttachmentEntity;
import io.myclaw.server.persistence.mapper.AttachmentMapper;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class AttachmentRepository {
    private final AttachmentMapper mapper;

    public AttachmentRepository(AttachmentMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<AttachmentEntity> findByIdAndOwnerEmail(String id, String owner) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<AttachmentEntity>()
                .eq(AttachmentEntity::getId, id).eq(AttachmentEntity::getOwnerEmail, owner).last("LIMIT 1")));
    }

    public List<AttachmentEntity> findBySessionIdAndOwnerEmailOrderByCreatedAt(String sessionId, String owner) {
        return mapper.selectList(new LambdaQueryWrapper<AttachmentEntity>().eq(AttachmentEntity::getSessionId, sessionId)
                .eq(AttachmentEntity::getOwnerEmail, owner).orderByAsc(AttachmentEntity::getCreatedAt));
    }

    public List<AttachmentEntity> findByMessageIdAndOwnerEmailOrderByCreatedAt(Long messageId, String owner) {
        return mapper.selectList(new LambdaQueryWrapper<AttachmentEntity>().eq(AttachmentEntity::getMessageId, messageId)
                .eq(AttachmentEntity::getOwnerEmail, owner).orderByAsc(AttachmentEntity::getCreatedAt));
    }

    public List<AttachmentEntity> findByMessageIds(List<Long> messageIds) {
        if (messageIds.isEmpty()) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<AttachmentEntity>().in(AttachmentEntity::getMessageId, messageIds));
    }

    public AttachmentEntity save(AttachmentEntity entity) {
        if (mapper.selectById(entity.getId()) == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return entity;
    }

    public void delete(AttachmentEntity entity) {
        mapper.deleteById(entity.getId());
    }
}
