package com.glw.backend.repository;

import com.glw.backend.model.entity.Account;

import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findByUsername(String username);

    Optional<Account> findById(long id);
}
