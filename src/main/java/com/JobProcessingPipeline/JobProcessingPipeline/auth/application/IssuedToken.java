package com.JobProcessingPipeline.JobProcessingPipeline.auth.application;

public record IssuedToken(String token, long expiresInSeconds) {
}

