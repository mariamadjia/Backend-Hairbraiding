package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.ChangePasswordRequest;
import org.example.backendbraiding.dto.LoginRequest;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTests {
    private AdminRepository admins;
    private PasswordEncoder passwords;
    private JwtTokenProvider tokens;
    private AuthService service;

    @BeforeEach
    void setUp() {
        admins = mock(AdminRepository.class);
        passwords = mock(PasswordEncoder.class);
        tokens = mock(JwtTokenProvider.class);
        service = new AuthService(admins, passwords, tokens, mock(EmailService.class),
                "google-client", 604800, mock(AdministratorService.class));
        when(tokens.generateToken(eq("Admin@Example.com"), eq("ROLE_ADMIN"), eq(0), anyLong(), anyBoolean()))
                .thenReturn("jwt");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passwordLoginNormalizesEmailCase() {
        Admin admin = activeAdmin();
        when(admins.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwords.matches("correct password", "hash")).thenReturn(true);
        LoginRequest request = new LoginRequest();
        request.setEmail(" ADMIN@example.com ");
        request.setPassword("correct password");

        service.login(request);

        verify(admins).findByEmailIgnoreCase("admin@example.com");
    }

    @Test
    void passwordChangeRevokesExistingSessions() {
        Admin admin = activeAdmin();
        when(admins.findByEmailIgnoreCase("Admin@Example.com")).thenReturn(Optional.of(admin));
        when(passwords.matches("old password", "hash")).thenReturn(true);
        when(passwords.encode("a much safer new password")).thenReturn("new-hash");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("Admin@Example.com", null));
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("old password");
        request.setNewPassword("a much safer new password");

        service.changePassword(request);

        assertEquals(1, admin.getSessionVersion());
        assertEquals("new-hash", admin.getPassword());
        verify(admins).save(admin);
    }

    @Test
    void activeSessionRenewalPreservesRememberDeviceChoice() {
        Admin admin = activeAdmin();
        when(admins.findByEmailIgnoreCase("Admin@Example.com")).thenReturn(Optional.of(admin));
        when(tokens.isRememberDeviceToken("current-jwt")).thenReturn(true);

        AuthService.SessionRefresh refreshed = service.refreshSession("Admin@Example.com", "current-jwt");

        assertEquals(true, refreshed.rememberDevice());
        assertEquals("jwt", refreshed.token());
        verify(tokens).generateToken("Admin@Example.com", "ROLE_ADMIN", 0, 604800000L, true);
    }

    private Admin activeAdmin() {
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setEmail("Admin@Example.com");
        admin.setUsername("admin");
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setPassword("hash");
        admin.setRole("ROLE_ADMIN");
        admin.setStatus("ACTIVE");
        admin.setPasswordConfigured(true);
        admin.setSessionVersion(0);
        return admin;
    }
}
