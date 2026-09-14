package com.register.backend.controller;

import com.register.backend.dto.request.LoginRequest;
import com.register.backend.dto.response.LoginResponse;
import com.register.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates an admin username/password and issues a JWT on success.
     *
     * @param request the login credentials
     * @return the issued token plus the authenticated user's username and role
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

}
