package com.taskmanagement.model;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    @NotBlank(message = "Username cannot be blank")
    private String username;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false, length = 255)
    @NotBlank(message = "Password cannot be blank")
    private String password;

    @Column(nullable = false, unique = true)
    @NotBlank(message = "Email cannot be blank")
    private String email;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Role cannot be null")
    private UserRole role;

    @Column
    private String profilePictureUrl;

    @Column(updatable = false)
    private LocalDateTime createdAt;    
    
    @Column
    private LocalDateTime updatedAt;
    
    private boolean isDeactivated = false;

    @Column(name = "security_stamp", length = 36)
    private String securityStamp = java.util.UUID.randomUUID().toString();

    @jakarta.persistence.Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    public boolean acceptsSession(String sessionId) {
        return securityStamp == null || (sessionId != null && sessionId.startsWith(securityStamp + "."));
    }

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
        if (this.displayName == null || this.displayName.isBlank()) {
            this.displayName = this.username;
        }
    }

    @PreUpdate
    protected void onUpdate(){
        this.updatedAt = LocalDateTime.now();
    }

    public void activate(){
        this.isDeactivated = false;
    }

    public void deactivate(){
        this.isDeactivated = true;
    }
}
