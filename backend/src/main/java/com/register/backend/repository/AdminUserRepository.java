package com.register.backend.repository;

import com.register.backend.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    /**
     * Finds an admin user by their exact username.
     *
     * @param username the username to look up
     * @return the matching admin user, or empty if none exists
     */
    Optional<AdminUser> findByUsername(String username);

}
