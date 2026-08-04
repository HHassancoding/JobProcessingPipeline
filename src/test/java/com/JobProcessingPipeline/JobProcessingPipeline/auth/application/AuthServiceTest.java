package com.JobProcessingPipeline.JobProcessingPipeline.auth.application;

import com.JobProcessingPipeline.JobProcessingPipeline.auth.domain.UserRole;
import com.JobProcessingPipeline.JobProcessingPipeline.auth.infra.UserEntity;
import com.JobProcessingPipeline.JobProcessingPipeline.auth.infra.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerCreatesHashedUserWithDefaultRole() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register("  Test@Example.com  ", "password123");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        verifyNoInteractions(jwtService);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("test@example.com", "password123"))
                .isInstanceOf(DuplicateEmailException.class);

        verifyNoInteractions(passwordEncoder, jwtService);
    }

    @Test
    void registerConvertsPersistenceConflictIntoDuplicateEmail() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(UserEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> authService.register("test@example.com", "password123"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        UserEntity user = new UserEntity();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole(UserRole.USER);

        when(userRepository.findByEmail("test@example.com")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn(new IssuedToken("token-value", 3600));

        IssuedToken issuedToken = authService.login("Test@Example.com", "password123");

        assertThat(issuedToken.token()).isEqualTo("token-value");
        assertThat(issuedToken.expiresInSeconds()).isEqualTo(3600);
        verify(jwtService).generateToken(user);
    }

    @Test
    void loginRejectsInvalidCredentialsWithoutLeakingDetails() {
        UserEntity user = new UserEntity();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole(UserRole.USER);

        when(userRepository.findByEmail("test@example.com")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("bad-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("test@example.com", "bad-password"))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(jwtService);
    }
}

