package io.myclaw.server.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.myclaw.server.persistence.entity.ChatMessageEntity;
import io.myclaw.server.persistence.mapper.ChatMessageMapper;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class ChatMessageRepository {
    private final ChatMessageMapper mapper;

    public ChatMessageRepository(ChatMessageMapper mapper) {
        this.mapper = mapper;
    }

    public List<ChatMessageEntity> findTop40BySessionIdAndStatusOrderByCreatedAtDesc(String sessionId, String status) {
        return mapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getSessionId, sessionId)
                .eq(ChatMessageEntity::getStatus, status).orderByDesc(ChatMessageEntity::getCreatedAt).last("LIMIT 40"));
    }

    public List<ChatMessageEntity> findTop40BySessionIdAndStatusesOrderByCreatedAtDesc(
            String sessionId, Collection<String> statuses
    ) {
        if (statuses == null || statuses.isEmpty()) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .in(ChatMessageEntity::getStatus, statuses)
                .orderByDesc(ChatMessageEntity::getCreatedAt)
                .last("LIMIT 40"));
    }
    public Optional<ChatMessageEntity> findFirstByRequestIdAndRole(String requestId, String role) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getRequestId, requestId).eq(ChatMessageEntity::getRole, role)
                .orderByAsc(ChatMessageEntity::getCreatedAt).last("LIMIT 1")));
    }

    public boolean existsByRequestIdAndRole(String requestId, String role) {
        return mapper.selectCount(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getRequestId, requestId).eq(ChatMessageEntity::getRole, role)) > 0;
    }

    public void deleteBySessionId(String sessionId) {
        mapper.delete(new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getSessionId, sessionId));
    }

    public List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId) {
        return mapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(ChatMessageEntity::getCreatedAt));
    }

    public List<Long> findIdsFrom(String sessionId, Long fromMessageId) {
        return mapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId).ge(ChatMessageEntity::getId, fromMessageId)
                .orderByAsc(ChatMessageEntity::getId)).stream().map(ChatMessageEntity::getId).toList();
    }

    public void deleteByIds(List<Long> ids) {
        if (!ids.isEmpty()) mapper.deleteByIds(ids);
    }

    public long countBySessionId(String sessionId) {
        return mapper.selectCount(new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getSessionId, sessionId));
    }

    public Optional<ChatMessageEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public ChatMessageEntity save(ChatMessageEntity entity) {
        if (entity.getId() == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return entity;
    }
}
