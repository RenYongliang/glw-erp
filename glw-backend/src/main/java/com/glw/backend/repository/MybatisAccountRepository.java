package com.glw.backend.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.glw.backend.mapper.UserMapper;
import com.glw.backend.model.entity.Account;
import com.glw.backend.model.entity.SysUser;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Profile("!mock")
public class MybatisAccountRepository implements AccountRepository {
    private final UserMapper mapper;

    public MybatisAccountRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<Account> findByUsername(String username) {
        return convert(
                mapper.selectOne(
                        Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)));
    }

    public Optional<Account> findById(long id) {
        return convert(mapper.selectById(id));
    }

    private Optional<Account> convert(SysUser user) {
        return Optional.ofNullable(user)
                .map(
                        u ->
                                new Account(
                                        u.getId(),
                                        u.getUsername(),
                                        u.getPasswordHash(),
                                        u.getName(),
                                        u.getRole(),
                                        u.getDepartment(),
                                        Boolean.TRUE.equals(u.getEnabled())));
    }
}
