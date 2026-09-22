package com.register.backend.controller;

import com.register.backend.dto.request.CreateAdminUserRequest;
import com.register.backend.dto.response.AdminUserSummaryResponse;
import com.register.backend.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only endpoints for listing and creating admin/consultant accounts.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * Lists all admin/consultant accounts.
     *
     * @return every admin user
     */
    @GetMapping
    public List<AdminUserSummaryResponse> listAdminUsers() {
        return adminUserService.listAdminUsers();
    }

    /**
     * Creates a new admin/consultant account.
     *
     * @param request the validated create request
     * @return the created account, 201
     */
    @PostMapping
    public ResponseEntity<AdminUserSummaryResponse> createAdminUser(@Valid @RequestBody CreateAdminUserRequest request) {
        AdminUserSummaryResponse response = adminUserService.createAdminUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
