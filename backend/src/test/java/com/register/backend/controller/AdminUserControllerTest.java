package com.register.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.register.backend.dto.request.CreateAdminUserRequest;
import com.register.backend.dto.response.AdminUserSummaryResponse;
import com.register.backend.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// This slice only loads AdminUserController, not the app's SecurityConfig. addFilters = false disables
// the security filter chain since this test isn't exercising security at all — access control for
// /api/admin/** is verified end to end for the existing admin endpoints in SecurityIntegrationTest, and
// the same SecurityConfig rule (`/api/admin/**` -> hasRole("ADMIN")) applies to this new controller too.
@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminUserService adminUserService;

    @Test
    void listAdminUsersReturnsSummariesFromService() throws Exception {
        when(adminUserService.listAdminUsers()).thenReturn(List.of(
                new AdminUserSummaryResponse(1L, "admin"),
                new AdminUserSummaryResponse(2L, "consultant1")));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].username").value("consultant1"));

        verify(adminUserService).listAdminUsers();
    }

    @Test
    void createAdminUserReturns201WithMappedResponseWhenRequestIsValid() throws Exception {
        CreateAdminUserRequest request = new CreateAdminUserRequest("consultant1", "password123");
        AdminUserSummaryResponse response = new AdminUserSummaryResponse(5L, "consultant1");

        when(adminUserService.createAdminUser(request)).thenReturn(response);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.username").value("consultant1"));

        verify(adminUserService).createAdminUser(request);
    }

    @Test
    void createAdminUserReturns400WhenUsernameIsBlank() throws Exception {
        CreateAdminUserRequest request = new CreateAdminUserRequest("", "password123");

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void createAdminUserReturns400WhenPasswordIsTooShort() throws Exception {
        CreateAdminUserRequest request = new CreateAdminUserRequest("consultant1", "short");

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        verifyNoInteractions(adminUserService);
    }

}
