package com.jpcore.labs.payment.auth;

import jakarta.persistence.*;

@Entity
@Table(name = "api_users")
public class ApiUser {
    @Id
    @Column(length = 100)
    private String username;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    protected ApiUser() { }
    public ApiUser(String username, String passwordHash, UserRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
}
