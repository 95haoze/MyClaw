package io.myclaw.server.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.myclaw.server.persistence.entity.MessageFeedbackEntity;
import io.myclaw.server.persistence.mapper.MessageFeedbackMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MessageFeedbackRepository {
    private final MessageFeedbackMapper mapper;

    public MessageFeedbackRepository(MessageFeedbackMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<MessageFeedbackEntity> findByMessageIdAndOwnerEmail(Long messageId, String owner) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<MessageFeedbackEntity>()
                .eq(MessageFeedbackEntity::getMessageId, messageId).eq(MessageFeedbackEntity::getOwnerEmail, owner).last("LIMIT 1")));
    }

    public void deleteByMessageIds(List<Long> messageIds) {
        if (!messageIds.isEmpty()) mapper.delete(new LambdaQueryWrapper<MessageFeedbackEntity>()
                .in(MessageFeedbackEntity::getMessageId, messageIds));
    }

    public MessageFeedbackEntity save(MessageFeedbackEntity entity) {
        if (entity.getId() == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return entity;
    }

    public void delete(MessageFeedbackEntity entity) {
        mapper.deleteById(entity.getId());
    }
}
