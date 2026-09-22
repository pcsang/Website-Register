package com.register.backend.service;

import com.register.backend.dto.request.CreateAdminUserRequest;
import com.register.backend.dto.response.AdminUserSummaryResponse;
import com.register.backend.entity.AdminUser;
import com.register.backend.repository.AdminUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages admin/consultant accounts: listing existing ones and creating new ones.
 *
 * <p>Every account created here is forced to {@code ROLE_ADMIN} server-side (matching {@code
 * AdminUserSeeder}'s seeded role) — there is no way for a caller to request a different role.
 */
@Service
public class AdminUserService {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates the service.
     *
     * @param adminUserRepository used to list and persist admin users
     * @param passwordEncoder     used to hash new accounts' passwords (same encoder bean used at login)
     */
    public AdminUserService(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Lists all admin/consultant accounts.
     *
     * @return every admin user mapped to a summary response, in no particular order
     */
    @Transactional(readOnly = true)
    public List<AdminUserSummaryResponse> listAdminUsers() {
        return adminUserRepository.findAll().stream()
                .map(user -> new AdminUserSummaryResponse(user.getId(), user.getUsername()))
                .toList();
    }

    /**
     * Creates a new admin account with a hashed password and a forced {@code ROLE_ADMIN} role.
     *
     * @param request the validated username/password to create the account with
     * @return the newly created account mapped to a summary response
     */
    @Transactional
    public AdminUserSummaryResponse createAdminUser(CreateAdminUserRequest request) {
        AdminUser user = new AdminUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(ADMIN_ROLE);

        AdminUser saved = adminUserRepository.save(user);
        return new AdminUserSummaryResponse(saved.getId(), saved.getUsername());
    }

}
