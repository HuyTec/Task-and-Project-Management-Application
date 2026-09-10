package com.taskmanagement.repository;
import com.taskmanagement.model.User;
import com.taskmanagement.model.UserRole;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.username = :username and u.isDeactivated = false")
    Optional<User> findForSecurityUpdate(@org.springframework.data.repository.query.Param("username") String username);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByRole(UserRole role);

    Optional<User> findByIdAndIsDeactivatedFalse(Long id);
    Optional<User> findByUsernameAndIsDeactivatedFalse(String username);
}
