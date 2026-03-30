package com.bank.auth.model;

import java.util.List;

public class User {

    private String userId;
    private String username;
    private String passwordHash;
    private List<String> roles;
    private boolean enabled;

    public User() {}

    public User(String userId, String username, String passwordHash, List<String> roles, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles;
        this.enabled = enabled;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
