package com.register.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Focused unit tests for {@link RateLimitingFilter}, exercised directly (no Spring context) so the limits
 * can be set tight and deterministic and so this suite can't interfere with, or be interfered by, the
 * shared-context {@code SecurityIntegrationTest}/{@code SubmissionApiIntegrationTest} suites that also hit
 * the two rate-limited endpoints under the app's real (much higher) configured defaults.
 */
class RateLimitingFilterTest {

    private static final int LIMIT = 3;

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        // findAndRegisterModules() picks up JavaTimeModule so ErrorResponse's LocalDateTime timestamp
        // serializes correctly - the app's real ObjectMapper bean has this too (via Spring Boot's Jackson
        // auto-configuration), this just replicates it for this standalone (no-Spring-context) test.
        filter = new RateLimitingFilter(new ObjectMapper().findAndRegisterModules(), LIMIT, LIMIT);
    }

    @Test
    void requestsUnderTheLimitPassThrough() throws Exception {
        String ip = "203.0.113.10";

        for (int i = 0; i < LIMIT; i++) {
            MockHttpServletRequest request = submissionsRequest(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
        }
    }

    @Test
    void requestBeyondTheLimitReturns429WithStandardErrorBody() throws Exception {
        String ip = "203.0.113.20";
        exhaustLimit(ip);

        MockHttpServletRequest request = submissionsRequest(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("\"status\":429");
        assertThat(response.getContentAsString()).contains("Too many requests. Please try again later.");
        assertThat(response.getContentAsString()).contains("/api/submissions");
    }

    @Test
    void differentClientIpsAreTrackedIndependently() throws Exception {
        String exhaustedIp = "203.0.113.30";
        exhaustLimit(exhaustedIp);

        // The exhausted IP is now blocked...
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(submissionsRequest(exhaustedIp), blockedResponse, mock(FilterChain.class));
        assertThat(blockedResponse.getStatus()).isEqualTo(429);

        // ...but a different IP is unaffected.
        MockHttpServletRequest otherRequest = submissionsRequest("203.0.113.31");
        MockHttpServletResponse otherResponse = new MockHttpServletResponse();
        FilterChain otherChain = mock(FilterChain.class);

        filter.doFilterInternal(otherRequest, otherResponse, otherChain);

        verify(otherChain, times(1)).doFilter(otherRequest, otherResponse);
    }

    @Test
    void xForwardedForHeaderIsUsedToIdentifyTheRealClientBehindAProxy() throws Exception {
        String realClientIp = "198.51.100.5";

        // All requests arrive from the same proxy (same getRemoteAddr()) but carry different real client
        // IPs via X-Forwarded-For - if the filter were reading getRemoteAddr() instead of the header, all
        // of these would incorrectly share one bucket.
        for (int i = 0; i < LIMIT; i++) {
            MockHttpServletRequest request = submissionsRequest("10.0.0.1");
            request.addHeader("X-Forwarded-For", realClientIp);
            filter.doFilterInternal(request, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        MockHttpServletRequest fourthRequest = submissionsRequest("10.0.0.1");
        fourthRequest.addHeader("X-Forwarded-For", realClientIp + ", 10.0.0.1");
        MockHttpServletResponse fourthResponse = new MockHttpServletResponse();

        filter.doFilterInternal(fourthRequest, fourthResponse, mock(FilterChain.class));

        assertThat(fourthResponse.getStatus()).isEqualTo(429);

        // A request from a *different* real client IP, proxied through the very same getRemoteAddr(),
        // must still pass through - proving the bucket key is the forwarded IP, not the proxy's address.
        MockHttpServletRequest differentClient = submissionsRequest("10.0.0.1");
        differentClient.addHeader("X-Forwarded-For", "198.51.100.9");
        MockHttpServletResponse differentClientResponse = new MockHttpServletResponse();
        FilterChain differentClientChain = mock(FilterChain.class);

        filter.doFilterInternal(differentClient, differentClientResponse, differentClientChain);

        verify(differentClientChain, times(1)).doFilter(differentClient, differentClientResponse);
    }

    @Test
    void nonRateLimitedRequestsAlwaysPassThroughRegardlessOfVolume() throws Exception {
        String ip = "203.0.113.40";

        for (int i = 0; i < LIMIT + 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/submissions");
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
        }
    }

    /**
     * Sends exactly {@link #LIMIT} requests for the given IP, consuming its entire window allowance.
     *
     * @param ip the client IP to exhaust the submissions limit for
     */
    private void exhaustLimit(String ip) throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            filter.doFilterInternal(submissionsRequest(ip), new MockHttpServletResponse(), mock(FilterChain.class));
        }
    }

    /**
     * Builds a {@code POST /api/submissions} request from the given remote address.
     *
     * @param remoteAddr the simulated {@code getRemoteAddr()} value
     * @return the built mock request
     */
    private MockHttpServletRequest submissionsRequest(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/submissions");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

}
