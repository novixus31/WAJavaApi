package com.chatin.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;

@Data
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password;

    private String role; // "superadmin", "admin", "manager", "user"

    @Indexed
    private String companyId;

    private String companyName;
    private Boolean isActive;
    private String profilePic;
    private Instant lastLogin;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * Hash the password before saving
     */
    public void hashPassword() {
        if (this.password != null && !this.password.startsWith("$2a$") && !this.password.startsWith("$2b$")) {
            this.password = encoder.encode(this.password);
        }
    }

    /**
     * Compare plain text password with hashed password
     */
    public boolean comparePassword(String candidatePassword) {
        return encoder.matches(candidatePassword, this.password);
    }
}
