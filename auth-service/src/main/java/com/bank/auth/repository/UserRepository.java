package com.bank.auth.repository;

import com.bank.auth.model.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);
}
