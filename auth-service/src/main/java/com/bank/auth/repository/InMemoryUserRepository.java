package com.bank.auth.repository;

import com.bank.auth.model.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> store = new ConcurrentHashMap<>();

    public InMemoryUserRepository() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User testUser = new User(
            UUID.randomUUID().toString(),
            "testuser",
            encoder.encode("password"),
            List.of("USER"),
            true
        );
        store.put(testUser.getUsername(), testUser);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(store.get(username));
    }
}
