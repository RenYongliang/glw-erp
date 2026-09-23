package com.glw.backend.user.repository;

import com.glw.backend.user.model.Account;

import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findByUsername(String username);

    Optional<Account> findById(long id);
}
