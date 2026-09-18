package io.myclaw.server.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.myclaw.server.persistence.entity.ChatSessionEntity;
import io.myclaw.server.persistence.mapper.ChatSessionMapper;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class ChatSessionRepository {
    private final ChatSessionMapper mapper;

    public ChatSessionRepository(ChatSessionMapper mapper) {
        this.mapper = mapper;
    }

    public List<ChatSessionEntity> findAllByOwnerEmailOrderByUpdatedAtDesc(String owner) {
        return mapper.selectList(new LambdaQueryWrapper<ChatSessionEntity>().eq(ChatSessionEntity::getOwnerEmail, owner)
                .orderByDesc(ChatSessionEntity::getUpdatedAt));
    }

    public Optional<ChatSessionEntity> findByIdAndOwnerEmail(String id, String owner) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, id).eq(ChatSessionEntity::getOwnerEmail, owner).last("LIMIT 1")));
    }

    public ChatSessionEntity save(ChatSessionEntity entity) {
        if (mapper.selectById(entity.getId()) == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return entity;
    }

    public void delete(ChatSessionEntity entity) {
        mapper.deleteById(entity.getId());
    }
}
