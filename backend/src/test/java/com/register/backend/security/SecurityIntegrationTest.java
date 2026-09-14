package com.register.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the JWT admin authentication flow: login (success/failure) and access to a
 * protected {@code /api/admin/**} endpoint with no token, an invalid token, an expired token, and a valid
 * token.
 *
 * <p>A full {@code @SpringBootTest} (real Spring Security filter chain, real datasource, real seeded
 * admin user) is used rather than a {@code @WebMvcTest} slice, because these tests need to exercise the
 * whole authentication pipeline end to end — the actual {@code AdminUserSeeder}, a real signed token
 * issued by {@code POST /api/auth/login}, and the real {@link JwtAuthenticationFilter}/
 * {@link SecurityConfig} wiring — rather than mocking any of those collaborators individually.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void healthEndpointIsPublicWithNoToken() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(adminUsername, adminPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value(adminUsername))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(adminUsername, "definitely-the-wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void loginWithUnknownUsernameReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("no-such-admin-user", "whatever")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void adminEndpointWithNoTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminEndpointWithGarbageTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", "Bearer this.is.not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminEndpointWithExpiredTokenReturns401() throws Exception {
        String expiredToken = generateExpiredToken();

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminEndpointWithValidTokenReturns200() throws Exception {
        String token = login(adminUsername, adminPassword);

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists());
    }

    private String loginBody(String username, String password) {
        return """
                {"username": "%s", "password": "%s"}
                """.formatted(username, password);
    }

    private String login(String username, String password) throws Exception {
        String responseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int tokenStart = responseBody.indexOf("\"token\":\"") + "\"token\":\"".length();
        int tokenEnd = responseBody.indexOf('"', tokenStart);
        return responseBody.substring(tokenStart, tokenEnd);
    }

    /**
     * Builds a JWT signed with the app's real configured secret but already expired, to verify the JWT
     * filter rejects expired tokens the same way it rejects garbage ones.
     */
    private String generateExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(3600);
        return Jwts.builder()
                .subject(adminUsername)
                .claim("role", "ROLE_ADMIN")
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();
    }

}
