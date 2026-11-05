package com.glw.backend.repository;

import com.glw.backend.model.entity.Account;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
@Profile("mock")
public class MockAccountRepository implements AccountRepository {
    private final List<Account> accounts;

    public MockAccountRepository(PasswordEncoder encoder) {
        accounts =
                List.of(
                        new Account(
                                1,
                                "admin",
                                encoder.encode("Admin123!"),
                                "管理员",
                                "ADMIN",
                                1L,
                                "总经办",
                                true),
                        new Account(
                                2,
                                "demo",
                                encoder.encode("Demo123!"),
                                "体验用户",
                                "USER",
                                2L,
                                "销售部",
                                true));
    }

    public Optional<Account> findByUsername(String username) {
        return accounts.stream().filter(a -> a.username().equals(username)).findFirst();
    }

    public Optional<Account> findById(long id) {
        return accounts.stream().filter(a -> a.id() == id).findFirst();
    }
}
