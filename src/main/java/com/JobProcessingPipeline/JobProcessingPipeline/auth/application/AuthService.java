package com.JobProcessingPipeline.JobProcessingPipeline.auth.application;

import com.JobProcessingPipeline.JobProcessingPipeline.auth.domain.UserRole;
import com.JobProcessingPipeline.JobProcessingPipeline.auth.infra.UserEntity;
import com.JobProcessingPipeline.JobProcessingPipeline.auth.infra.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public void register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("auth.register.conflict email={}", normalizedEmail);
            throw new DuplicateEmailException(normalizedEmail);
        }

        UserEntity user = new UserEntity();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(UserRole.USER);

        try {
            userRepository.save(user);
            log.info("auth.register.success email={}", normalizedEmail);
        } catch (DataIntegrityViolationException ex) {
            log.warn("auth.register.conflict email={}", normalizedEmail);
            throw new DuplicateEmailException(normalizedEmail, ex);
        }
    }

    @Transactional(readOnly = true)
    public IssuedToken login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);

        UserEntity user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("auth.login.failure email={}", normalizedEmail);
                    return new InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("auth.login.failure email={}", normalizedEmail);
            throw new InvalidCredentialsException();
        }

        IssuedToken issuedToken = jwtService.generateToken(user);
        log.info("auth.login.success email={}", normalizedEmail);
        return issuedToken;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

