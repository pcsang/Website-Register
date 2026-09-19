package com.register.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.register.backend.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory, per-client-IP rate limiter for the two fully public, unauthenticated endpoints that
 * would otherwise have zero abuse protection: {@code POST /api/submissions} (spam/DoS-by-volume risk) and
 * {@code POST /api/auth/login} (brute-force risk against the single seeded admin account). Every other
 * request is passed through untouched.
 *
 * <p>Uses a <b>fixed 1-minute window</b> per client IP per endpoint, not a sliding window: the window
 * boundary is simply {@code currentTimeMillis} truncated down to the minute. This is the deliberately
 * simpler of the two algorithms — it allows a burst of up to {@code 2x} the limit right at a window
 * boundary (e.g. 5 requests at 0:59, 5 more at 1:00), which is an acceptable tradeoff for a small public
 * app's realistic threat model (slowing down casual spam/brute-force, not defeating a determined attacker)
 * per this phase's explicit "do not over-engineer" instruction. A true sliding window would need to track
 * per-request timestamps (or a rolling counter across two windows) for a small precision gain that isn't
 * worth the extra complexity here.
 *
 * <p>Tracking is a {@link ConcurrentHashMap} keyed by {@code "<endpoint>:<clientIp>"}, updated atomically
 * via {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)} (thread-safe per key,
 * without needing a separate lock or {@code AtomicInteger} — the whole read-check-write happens inside one
 * atomic map operation). Entries for windows that have expired are swept out periodically (see
 * {@link #cleanupIfDue(long)}) so the map doesn't grow unbounded as new client IPs show up over the
 * lifetime of a long-running instance.
 *
 * <p>The real client IP is read from the first entry of {@code X-Forwarded-For} when present, falling back
 * to {@link HttpServletRequest#getRemoteAddr()} otherwise. This app is deployed on Render behind a
 * reverse proxy/load balancer, so {@code getRemoteAddr()} alone would return the proxy's IP for every
 * request — making a per-IP limiter useless in production. Local dev has no proxy in front, so the header
 * is simply absent there and the fallback applies.
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String SUBMISSIONS_PATH = "/api/submissions";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String XFF_HEADER = "X-Forwarded-For";

    private static final long WINDOW_MILLIS = 60_000L;
    private static final long CLEANUP_INTERVAL_MILLIS = 5 * 60_000L;
    private static final long ENTRY_TTL_MILLIS = 2 * WINDOW_MILLIS;

    private final ObjectMapper objectMapper;
    private final int submissionsPerMinute;
    private final int loginAttemptsPerMinute;

    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMillis = new AtomicLong(System.currentTimeMillis());

    /**
     * Creates the filter.
     *
     * @param objectMapper           used to serialize the 429 {@link ErrorResponse} body
     * @param submissionsPerMinute   max allowed {@code POST /api/submissions} requests per client IP per
     *                               1-minute window, bound from {@code app.rate-limit.submissions-per-minute}
     * @param loginAttemptsPerMinute max allowed {@code POST /api/auth/login} requests per client IP per
     *                               1-minute window, bound from {@code app.rate-limit.login-attempts-per-minute}
     */
    public RateLimitingFilter(ObjectMapper objectMapper, int submissionsPerMinute, int loginAttemptsPerMinute) {
        this.objectMapper = objectMapper;
        this.submissionsPerMinute = submissionsPerMinute;
        this.loginAttemptsPerMinute = loginAttemptsPerMinute;
    }

    /**
     * Applies the per-IP rate limit to the two rate-limited endpoints, writing a 429 response and short
     * circuiting the filter chain if the limit has already been reached for the current window; every
     * other request passes through unmodified.
     *
     * @param request     the incoming request
     * @param response    the outgoing response
     * @param filterChain the remaining filter chain
     * @throws ServletException if the underlying filter chain throws one
     * @throws IOException      if the underlying filter chain (or writing the 429 body) throws one
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Endpoint endpoint = matchEndpoint(request);
        if (endpoint == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        cleanupIfDue(now);

        String clientIp = extractClientIp(request);
        String key = endpoint.name() + ":" + clientIp;
        long currentWindowStart = now - (now % WINDOW_MILLIS);

        Window updated = buckets.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart() != currentWindowStart) {
                return new Window(currentWindowStart, 1);
            }
            return new Window(existing.windowStart(), existing.count() + 1);
        });

        if (updated.count() > endpoint.limit()) {
            writeTooManyRequests(response, request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Determines whether the request targets one of the two rate-limited endpoints and, if so, which limit
     * applies.
     *
     * @param request the incoming request
     * @return the matching {@link Endpoint} (bucket name + configured limit), or {@code null} if the
     *         request is not rate-limited
     */
    private Endpoint matchEndpoint(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        if (SUBMISSIONS_PATH.equals(uri)) {
            return new Endpoint("submissions", submissionsPerMinute);
        }
        if (LOGIN_PATH.equals(uri)) {
            return new Endpoint("login", loginAttemptsPerMinute);
        }
        return null;
    }

    /**
     * Extracts the real client IP, preferring the first (left-most, i.e. original client) entry of the
     * {@code X-Forwarded-For} header set by the reverse proxy in production, and falling back to
     * {@link HttpServletRequest#getRemoteAddr()} when the header is absent (local dev).
     *
     * @param request the incoming request
     * @return the resolved client IP
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(XFF_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Writes the 429 error body, in the API's standard {@link ErrorResponse} JSON shape, for a request
     * rejected by the rate limiter.
     *
     * @param response the response to write to
     * @param request  the rejected request
     * @throws IOException if writing the response body fails
     */
    private void writeTooManyRequests(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too many requests. Please try again later.",
                request.getRequestURI()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /**
     * Opportunistically sweeps expired window entries out of {@link #buckets} at most once per
     * {@link #CLEANUP_INTERVAL_MILLIS}, so the map doesn't grow unbounded as new client IPs appear over the
     * lifetime of a long-running instance. Guarded by a compare-and-set on {@link #lastCleanupMillis} so
     * concurrent requests don't all perform the sweep at once.
     *
     * @param now the current time in epoch millis, as observed by the caller
     */
    private void cleanupIfDue(long now) {
        long last = lastCleanupMillis.get();
        if (now - last < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastCleanupMillis.compareAndSet(last, now)) {
            return;
        }
        buckets.entrySet().removeIf(entry -> now - entry.getValue().windowStart() > ENTRY_TTL_MILLIS);
    }

    /**
     * A rate-limited endpoint's bucket name (used as part of the tracking map key) and configured
     * per-minute limit.
     *
     * @param name  short identifier for this endpoint, used to namespace its bucket keys
     * @param limit the max allowed requests per client IP per 1-minute window
     */
    private record Endpoint(String name, int limit) {
    }

    /**
     * A fixed 1-minute counting window: the epoch-millis start of the window, and how many requests have
     * been counted in it so far.
     *
     * @param windowStart the epoch-millis start of this window
     * @param count       the number of requests counted in this window so far
     */
    private record Window(long windowStart, int count) {
    }

}
