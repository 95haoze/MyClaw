package io.myclaw.server.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.myclaw.server.persistence.entity.ChatToolExecutionEntity;
import io.myclaw.server.persistence.mapper.ChatToolExecutionMapper;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class ChatToolExecutionRepository {
    private final ChatToolExecutionMapper mapper;
    public ChatToolExecutionRepository(ChatToolExecutionMapper mapper){this.mapper=mapper;}
    public void saveAll(List<ChatToolExecutionEntity> executions){executions.forEach(mapper::insert);}
    public List<ChatToolExecutionEntity> findByMessageId(Long messageId){
        if(messageId==null)return List.of();
        return mapper.selectList(new LambdaQueryWrapper<ChatToolExecutionEntity>()
                .eq(ChatToolExecutionEntity::getMessageId,messageId)
                .orderByAsc(ChatToolExecutionEntity::getSequence));
    }
}
