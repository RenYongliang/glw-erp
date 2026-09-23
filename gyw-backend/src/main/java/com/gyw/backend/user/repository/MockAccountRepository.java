package com.gyw.backend.user.repository;

import com.gyw.backend.user.model.Account;

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
                                "运营管理部",
                                true),
                        new Account(
                                2,
                                "demo",
                                encoder.encode("Demo123!"),
                                "体验用户",
                                "USER",
                                "业务体验组",
                                true));
    }

    public Optional<Account> findByUsername(String username) {
        return accounts.stream().filter(a -> a.username().equals(username)).findFirst();
    }

    public Optional<Account> findById(long id) {
        return accounts.stream().filter(a -> a.id() == id).findFirst();
    }
}
