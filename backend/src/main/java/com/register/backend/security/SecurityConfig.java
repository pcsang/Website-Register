package com.register.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration for stateless JWT admin authentication.
 *
 * <p>Only {@code /api/admin/**} is protected (requires a valid JWT carrying {@code ROLE_ADMIN}); every
 * other endpoint — including {@code GET /api/health}, {@code POST /api/submissions},
 * {@code POST /api/auth/login}, and the existing Swagger/Actuator endpoints — is left exactly as
 * accessible as it was before this phase. This keeps the change scoped to what the phase actually asks
 * for (protecting the admin API) without incidentally locking down unrelated routes that were already
 * public.
 *
 * <p>No {@code UserDetailsService}/{@code AuthenticationManager} bean is defined: login is handled
 * directly in {@code AuthService} against {@link com.register.backend.repository.AdminUserRepository},
 * and per-request authentication is handled by {@link JwtAuthenticationFilter} purely from the signed
 * token's claims (no database lookup per request). This keeps the security configuration minimal and
 * avoids Spring Boot's auto-configured default in-memory user (and its generated-password log line) that
 * would otherwise appear once {@code spring-boot-starter-security} is on the classpath with no
 * authentication mechanism configured.
 *
 * <p>Phase 25 added {@link RateLimitingFilter}, registered ahead of {@link JwtAuthenticationFilter} (which
 * in turn runs ahead of {@code UsernamePasswordAuthenticationFilter}). Rate limiting is deliberately the
 * very first thing the security chain does for the two endpoints it covers: it's the cheapest possible
 * check (an in-memory map lookup, no cryptographic work), so there's no reason to spend effort parsing/
 * verifying a JWT on a request that's about to be rejected with 429 anyway.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final ObjectMapper objectMapper;
    private final int rateLimitSubmissionsPerMinute;
    private final int rateLimitLoginAttemptsPerMinute;

    /**
     * Creates the security configuration.
     *
     * @param jwtService                       used to build the stateless JWT authentication filter
     * @param restAuthenticationEntryPoint     writes the standard 401 error body for unauthenticated requests
     * @param restAccessDeniedHandler          writes the standard 403 error body for unauthorized requests
     * @param objectMapper                     used by {@link RateLimitingFilter} to serialize its 429 error body
     * @param rateLimitSubmissionsPerMinute    max {@code POST /api/submissions} requests per client IP per
     *                                         minute, bound from {@code app.rate-limit.submissions-per-minute}
     * @param rateLimitLoginAttemptsPerMinute  max {@code POST /api/auth/login} requests per client IP per
     *                                         minute, bound from {@code app.rate-limit.login-attempts-per-minute}
     */
    public SecurityConfig(JwtService jwtService,
                           RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                           RestAccessDeniedHandler restAccessDeniedHandler,
                           ObjectMapper objectMapper,
                           @Value("${app.rate-limit.submissions-per-minute}") int rateLimitSubmissionsPerMinute,
                           @Value("${app.rate-limit.login-attempts-per-minute}") int rateLimitLoginAttemptsPerMinute) {
        this.jwtService = jwtService;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
        this.objectMapper = objectMapper;
        this.rateLimitSubmissionsPerMinute = rateLimitSubmissionsPerMinute;
        this.rateLimitLoginAttemptsPerMinute = rateLimitLoginAttemptsPerMinute;
    }

    /**
     * Exposes the password encoder used both to hash the seeded admin password and to verify passwords at
     * login.
     *
     * @return a {@link BCryptPasswordEncoder}
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Builds the stateless security filter chain: CORS reuses the existing {@code WebMvcConfigurer}-based
     * configuration, CSRF is disabled (not applicable to a stateless, non-cookie-based API), no HTTP
     * session is created, only {@code /api/admin/**} requires authentication (with {@code ROLE_ADMIN}),
     * the JWT filter runs ahead of Spring Security's default username/password filter so a valid bearer
     * token populates the security context before authorization checks run, and the rate limiting filter
     * runs ahead of that (see this class's Javadoc for why).
     *
     * @param http the security configuration builder
     * @return the built filter chain
     * @throws Exception if the security DSL configuration fails to build
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService);
        RateLimitingFilter rateLimitingFilter = new RateLimitingFilter(
                objectMapper, rateLimitSubmissionsPerMinute, rateLimitLoginAttemptsPerMinute);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitingFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

}
