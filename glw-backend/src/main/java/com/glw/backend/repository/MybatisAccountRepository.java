package com.glw.backend.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.glw.backend.mapper.SysDepartmentMapper;
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
    private final SysDepartmentMapper departments;

    public MybatisAccountRepository(UserMapper mapper, SysDepartmentMapper departments) {
        this.mapper = mapper;
        this.departments = departments;
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
                        u -> {
                            String departmentName =
                                    u.getDepartmentId() == null
                                            ? u.getDepartment()
                                            : Optional.ofNullable(
                                                            departments.selectById(
                                                                    u.getDepartmentId()))
                                                    .map(
                                                            d -> d.getName())
                                                    .orElse(u.getDepartment());
                            return new Account(
                                    u.getId(),
                                    u.getUsername(),
                                    u.getPasswordHash(),
                                    u.getName(),
                                    u.getRole(),
                                    u.getDepartmentId(),
                                    departmentName,
                                    Boolean.TRUE.equals(u.getEnabled()));
                        });
    }
}
