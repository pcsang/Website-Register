package com.register.backend.service;

import com.register.backend.dto.request.CreateAdminUserRequest;
import com.register.backend.dto.response.AdminUserSummaryResponse;
import com.register.backend.entity.AdminUser;
import com.register.backend.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    void listAdminUsersReturnsMappedSummariesForEveryAdminUser() {
        AdminUser first = new AdminUser();
        first.setId(1L);
        first.setUsername("admin");
        first.setPasswordHash("hashed-1");
        first.setRole("ROLE_ADMIN");

        AdminUser second = new AdminUser();
        second.setId(2L);
        second.setUsername("consultant1");
        second.setPasswordHash("hashed-2");
        second.setRole("ROLE_ADMIN");

        when(adminUserRepository.findAll()).thenReturn(List.of(first, second));

        List<AdminUserSummaryResponse> actual = adminUserService.listAdminUsers();

        assertThat(actual).containsExactly(
                new AdminUserSummaryResponse(1L, "admin"),
                new AdminUserSummaryResponse(2L, "consultant1"));
    }

    @Test
    void createAdminUserHashesPasswordForcesAdminRoleAndSaves() {
        CreateAdminUserRequest request = new CreateAdminUserRequest("consultant1", "plaintext-pw");

        when(passwordEncoder.encode("plaintext-pw")).thenReturn("hashed-pw");
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(invocation -> {
            AdminUser saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        AdminUserSummaryResponse actual = adminUserService.createAdminUser(request);

        assertThat(actual).isEqualTo(new AdminUserSummaryResponse(5L, "consultant1"));

        ArgumentCaptor<AdminUser> savedCaptor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserRepository).save(savedCaptor.capture());
        AdminUser savedUser = savedCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("consultant1");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-pw");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("plaintext-pw");
        assertThat(savedUser.getRole()).isEqualTo("ROLE_ADMIN");
    }

}
