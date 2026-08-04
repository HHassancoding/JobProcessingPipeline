package com.JobProcessingPipeline.JobProcessingPipeline.auth.api;

import com.JobProcessingPipeline.JobProcessingPipeline.auth.application.AuthService;
import com.JobProcessingPipeline.JobProcessingPipeline.auth.application.DuplicateEmailException;
import com.JobProcessingPipeline.JobProcessingPipeline.auth.application.IssuedToken;
import com.JobProcessingPipeline.JobProcessingPipeline.auth.application.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setValidator(validator)
                .build();
    }

    @Test
    void registerReturnsCreated() throws Exception {
        doNothing().when(authService).register("test@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));
    }

    @Test
    void registerReturnsConflictForDuplicateEmail() throws Exception {
        org.mockito.Mockito.doThrow(new DuplicateEmailException("test@example.com"))
                .when(authService).register("test@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"password123"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void loginReturnsTokenResponse() throws Exception {
        when(authService.login("test@example.com", "password123"))
                .thenReturn(new IssuedToken("jwt-token", 3600));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginReturnsUnauthorizedForInvalidCredentials() throws Exception {
        org.mockito.Mockito.when(authService.login("test@example.com", "password123"))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"password123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerReturnsBadRequestForInvalidInput() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"short"}
                                """))
                .andExpect(status().isBadRequest());
    }
}



