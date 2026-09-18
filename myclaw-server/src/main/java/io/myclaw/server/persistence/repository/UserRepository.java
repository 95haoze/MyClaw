package io.myclaw.server.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.myclaw.server.persistence.entity.UserEntity;
import io.myclaw.server.persistence.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {
    private final UserMapper mapper;

    public UserRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<UserEntity> findByEmailIgnoreCase(String email) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .apply("LOWER(email) = LOWER({0})", email).last("LIMIT 1")));
    }

    public boolean existsByEmailIgnoreCase(String email) {
        return findByEmailIgnoreCase(email).isPresent();
    }

    public UserEntity save(UserEntity entity) {
        if (entity.getId() == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return entity;
    }
}
