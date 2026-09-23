package com.gyw.backend.user.repository;

import com.gyw.backend.user.model.Account;

import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findByUsername(String username);

    Optional<Account> findById(long id);
}
