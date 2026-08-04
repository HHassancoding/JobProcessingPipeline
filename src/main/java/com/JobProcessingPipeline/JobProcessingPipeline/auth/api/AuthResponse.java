package com.JobProcessingPipeline.JobProcessingPipeline.auth.api;

import com.JobProcessingPipeline.JobProcessingPipeline.auth.application.IssuedToken;

public record AuthResponse(String token, long expiresIn) {

    public static AuthResponse from(IssuedToken issuedToken) {
        return new AuthResponse(issuedToken.token(), issuedToken.expiresInSeconds());
    }
}

