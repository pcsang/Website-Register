package com.register.backend.service;

import com.register.backend.dto.request.LoginRequest;
import com.register.backend.dto.response.LoginResponse;
import com.register.backend.entity.AdminUser;
import com.register.backend.exception.InvalidCredentialsException;
import com.register.backend.repository.AdminUserRepository;
import com.register.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles admin login: verifies credentials against the stored {@link AdminUser} and issues a signed JWT.
 */
@Service
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Authenticates an admin login attempt and issues a JWT on success.
     *
     * @param request the submitted username/password
     * @return the issued token plus the authenticated user's username and role
     * @throws InvalidCredentialsException if the username doesn't exist or the password doesn't match
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AdminUser user = adminUserRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(), user.getRole());
    }

}
