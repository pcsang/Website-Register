package com.register.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.register.backend.entity.Submission;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for the submission APIs — {@code POST /api/submissions},
 * {@code GET/PATCH /api/admin/submissions[...]}, and {@code GET /api/admin/dashboard/summary} — using a
 * real Spring context ({@code @SpringBootTest}), real {@code MockMvc}-driven HTTP requests, the real
 * {@code SubmissionService}/{@code SubmissionRepository}/Hibernate stack, and real JSON
 * (de)serialization, as opposed to the pre-existing {@code @WebMvcTest} slices elsewhere in this package
 * which mock the service layer.
 *
 * <h2>Database: H2 (in-memory) vs. PostgreSQL Testcontainers</h2>
 * Production and local development both run against real PostgreSQL (see {@code application.yml}). The
 * ideal integration-test database would therefore be PostgreSQL itself, most reproducibly via
 * Testcontainers (a real, disposable PostgreSQL container per test run) — that gives true dialect parity
 * with production. This dev machine, however, has no Docker available ({@code docker --version} fails to
 * resolve), so Testcontainers cannot run here. This test class instead uses an in-memory H2 database
 * (profile {@code test}, see {@code src/test/resources/application-test.yml}), in
 * {@code MODE=PostgreSQL} compatibility mode to narrow — not eliminate — the dialect gap, with
 * {@code ddl-auto: create-drop} for a fresh, isolated schema per test run.
 * <p>
 * The tradeoff is real, not theoretical, in this codebase's own history: Phase 6's admin submission list
 * shipped a bug — {@code function lower(bytea) does not exist} — caused by PostgreSQL/Hibernate's type
 * inference for a {@code null}-valued bind parameter inside {@code LOWER(...)}, a quirk that is specific
 * to the PostgreSQL JDBC driver/dialect and was never caught by mocked-repository unit tests; it only
 * surfaced when exercised against a real PostgreSQL instance. An H2-backed test suite like this one would
 * not have caught that class of bug either (H2's type inference differs from PostgreSQL's), so this suite
 * is deliberately not a substitute for eventually also verifying against real PostgreSQL — it verifies the
 * application/HTTP/service/mapping layers thoroughly and hermetically, while genuine PostgreSQL-specific
 * dialect quirks remain a residual risk this suite cannot catch. Recommendation: reconsider migrating this
 * class (or adding a parallel one) to PostgreSQL Testcontainers once Docker is available in the dev/CI
 * environment, to close that gap.
 * <p>
 * This class does not touch or depend on the local/production PostgreSQL instance in any way — it is
 * fully hermetic and was verified to pass with the local PostgreSQL service stopped (see
 * {@code CHECKLIST.md}'s Phase 19 log entry for the exact verification steps and output).
 *
 * <h2>Isolation between test methods</h2>
 * The class is annotated {@code @Transactional} (Spring's test-transaction semantics): every test method
 * runs inside a transaction that's rolled back once the method finishes, so submissions seeded by one test
 * (directly via {@link SubmissionRepository} or via the create endpoint) never leak into another test.
 *
 * <h2>Admin authentication</h2>
 * {@code /api/admin/**} requires a valid {@code Authorization: Bearer <token>} for a {@code ROLE_ADMIN}
 * user (Phase 16). Rather than disabling the security filter chain, a real JWT is obtained per test via an
 * actual {@code POST /api/auth/login} call in {@link #obtainAdminToken()} (seeded admin credentials from
 * {@code app.admin.username}/{@code app.admin.password}), matching the more realistic full-stack intent of
 * this phase. {@code POST /api/submissions} is exercised with no token, matching production (it's public).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SubmissionApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    private String adminToken;

    /**
     * Logs in as the seeded admin user before every test, so admin-protected requests can attach a real
     * bearer token.
     *
     * @throws Exception if the login request itself fails unexpectedly
     */
    @BeforeEach
    void setUp() throws Exception {
        adminToken = obtainAdminToken();
    }

    @Test
    void createSubmissionReturns201WithCreatedBodyOnValidInput() throws Exception {
        String requestBody = """
                {"fullName": "Jane Doe", "email": "jane@example.com", "phone": "0123456789", "message": "Hello there"}
                """;

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.phone").value("0123456789"))
                .andExpect(jsonPath("$.message").value("Hello there"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void createSubmissionReturns400WithFieldErrorsOnValidationFailure() throws Exception {
        String requestBody = """
                {"fullName": "", "email": "not-an-email"}
                """;

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void listSubmissionsReturnsCorrectPagination() throws Exception {
        for (int i = 1; i <= 25; i++) {
            seedSubmission("Applicant " + i, "applicant" + i + "@example.com", SubmissionStatus.NEW);
        }

        mockMvc.perform(get("/api/admin/submissions")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/admin/submissions")
                        .param("page", "2")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.page").value(2));
    }

    @Test
    void listSubmissionsFiltersBySearchSubstring() throws Exception {
        seedSubmission("Alice Wonderland", "alice@example.com", SubmissionStatus.NEW);
        seedSubmission("Bob Marley", "bob@example.com", SubmissionStatus.NEW);
        seedSubmission("Carol Danvers", "carol@example.com", SubmissionStatus.NEW);

        mockMvc.perform(get("/api/admin/submissions")
                        .param("search", "alice")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Alice Wonderland"));
    }

    @Test
    void listSubmissionsFiltersByStatus() throws Exception {
        seedSubmission("New Person", "new@example.com", SubmissionStatus.NEW);
        seedSubmission("In Progress Person", "progress@example.com", SubmissionStatus.IN_PROGRESS);
        seedSubmission("Completed Person", "completed@example.com", SubmissionStatus.COMPLETED);

        mockMvc.perform(get("/api/admin/submissions")
                        .param("status", "IN_PROGRESS")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("In Progress Person"))
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"));
    }

    @Test
    void listSubmissionsFiltersBySearchAndStatusCombined() throws Exception {
        seedSubmission("Alice Wonderland", "alice@example.com", SubmissionStatus.NEW);
        seedSubmission("Alice In Progress", "alice.progress@example.com", SubmissionStatus.IN_PROGRESS);
        seedSubmission("Bob In Progress", "bob@example.com", SubmissionStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/admin/submissions")
                        .param("search", "alice")
                        .param("status", "IN_PROGRESS")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Alice In Progress"));
    }

    @Test
    void getSubmissionByIdReturns200WithCorrectBodyWhenItExists() throws Exception {
        Submission submission = seedSubmission("Jane Doe", "jane@example.com", SubmissionStatus.NEW);

        mockMvc.perform(get("/api/admin/submissions/{id}", submission.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submission.getId()))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void getSubmissionByIdReturns404WhenItDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/admin/submissions/{id}", 999999L)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Submission not found with id: 999999"));
    }

    @Test
    void updateStatusReturns200WithUpdatedBodyOnValidStatus() throws Exception {
        Submission submission = seedSubmission("Jane Doe", "jane@example.com", SubmissionStatus.NEW);

        mockMvc.perform(patch("/api/admin/submissions/{id}/status", submission.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submission.getId()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void updateStatusReturns400OnInvalidStatusValue() throws Exception {
        Submission submission = seedSubmission("Jane Doe", "jane@example.com", SubmissionStatus.NEW);

        mockMvc.perform(patch("/api/admin/submissions/{id}/status", submission.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"BOGUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateStatusReturns404WhenSubmissionDoesNotExist() throws Exception {
        mockMvc.perform(patch("/api/admin/submissions/{id}/status", 999999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"COMPLETED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void dashboardSummaryReflectsSeededData() throws Exception {
        seedSubmission("New One", "new1@example.com", SubmissionStatus.NEW);
        seedSubmission("New Two", "new2@example.com", SubmissionStatus.NEW);
        seedSubmission("In Progress One", "progress1@example.com", SubmissionStatus.IN_PROGRESS);
        seedSubmission("Completed One", "completed1@example.com", SubmissionStatus.COMPLETED);

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.new").value(2))
                .andExpect(jsonPath("$.inProgress").value(1))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.submittedToday").value(4));
    }

    /**
     * Persists a submission directly via the repository (bypassing the create endpoint, which always
     * forces {@code NEW}), so tests can seed data with an arbitrary status.
     *
     * @param fullName the submitter's full name
     * @param email    the submitter's email
     * @param status   the status to seed the submission with
     * @return the persisted, flushed submission (with its generated ID populated)
     */
    private Submission seedSubmission(String fullName, String email, SubmissionStatus status) {
        Submission submission = new Submission();
        submission.setFullName(fullName);
        submission.setEmail(email);
        submission.setPhone("0123456789");
        submission.setMessage("Seeded for integration test");
        submission.setStatus(status);
        return submissionRepository.saveAndFlush(submission);
    }

    /**
     * Logs in as the seeded admin user via a real {@code POST /api/auth/login} call and extracts the JWT
     * from the response body.
     *
     * @return the issued bearer token
     * @throws Exception if the login request itself fails unexpectedly
     */
    private String obtainAdminToken() throws Exception {
        String requestBody = objectMapper.writeValueAsString(new LoginRequestBody(adminUsername, adminPassword));

        String responseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(responseBody).get("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private record LoginRequestBody(String username, String password) {
    }

}
