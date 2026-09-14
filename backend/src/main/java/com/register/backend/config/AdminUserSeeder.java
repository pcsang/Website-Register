package com.register.backend.config;

import com.register.backend.entity.AdminUser;
import com.register.backend.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds exactly one admin user on startup, from {@code app.admin.username}/{@code app.admin.password}
 * ({@code ADMIN_USERNAME}/{@code ADMIN_PASSWORD} env vars), if and only if the {@code admin_users} table
 * is currently empty.
 *
 * <p>There is no admin signup/registration endpoint by design (this is an internal tool, not public
 * signup), so without this seeder the table would stay empty forever and nobody could ever log in. Safe
 * to run on every restart: it only ever acts when the table has zero rows, so it never overwrites or
 * duplicates an existing admin account.
 */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedUsername;
    private final String seedPassword;

    /**
     * Creates the seeder.
     *
     * @param adminUserRepository used to check whether any admin user already exists and to persist the
     *                            seeded one
     * @param passwordEncoder     used to hash the seeded password (same encoder bean used at login)
     * @param seedUsername        the username to seed, bound from {@code app.admin.username}
     * @param seedPassword        the plaintext password to hash and seed, bound from {@code app.admin.password}
     */
    public AdminUserSeeder(AdminUserRepository adminUserRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${app.admin.username}") String seedUsername,
                            @Value("${app.admin.password}") String seedPassword) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedUsername = seedUsername;
        this.seedPassword = seedPassword;
    }

    /**
     * Creates the seeded admin user if the {@code admin_users} table is currently empty.
     *
     * @param args the application startup arguments (unused)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminUserRepository.count() > 0) {
            return;
        }

        AdminUser admin = new AdminUser();
        admin.setUsername(seedUsername);
        admin.setPasswordHash(passwordEncoder.encode(seedPassword));
        admin.setRole(ADMIN_ROLE);
        adminUserRepository.save(admin);

        log.info("Seeded initial admin user '{}' (admin_users table was empty)", seedUsername);
    }

}
