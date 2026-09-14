package com.register.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the {@code Authorization: Bearer <token>} header on every request, and — if it carries a valid,
 * unexpired JWT — populates the {@link SecurityContextHolder} with an authenticated principal built
 * directly from the token's claims (username + role). No database lookup is performed per request; the
 * signed token is trusted as-is, which is what makes this authentication stateless.
 *
 * <p>A missing, malformed, or expired token is not an error here — the filter simply leaves the request
 * unauthenticated and lets it continue down the chain. Whether that's acceptable is decided later by the
 * authorization rules in {@code SecurityConfig}: a public endpoint proceeds fine, a protected endpoint is
 * rejected with 401 by {@link RestAuthenticationEntryPoint}.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    /**
     * Creates the filter.
     *
     * @param jwtService used to parse and verify incoming bearer tokens
     */
    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Extracts and validates a bearer JWT (if present) and, when valid, sets the authenticated principal
     * on the security context before continuing the filter chain.
     *
     * @param request     the incoming request
     * @param response    the outgoing response
     * @param filterChain the remaining filter chain
     * @throws ServletException if the underlying filter chain throws one
     * @throws IOException      if the underlying filter chain throws one
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parseClaims(token);
                String username = claims.getSubject();
                String role = jwtService.extractRole(claims);

                if (username != null && role != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            username, null, List.of(new SimpleGrantedAuthority(role)));
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Rejected invalid JWT on {}: {}", request.getRequestURI(), ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

}
