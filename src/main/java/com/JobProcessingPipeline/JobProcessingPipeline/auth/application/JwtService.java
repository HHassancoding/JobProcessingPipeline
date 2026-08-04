package com.JobProcessingPipeline.JobProcessingPipeline.auth.application;

import com.JobProcessingPipeline.JobProcessingPipeline.auth.infra.UserEntity;

public interface JwtService {

    IssuedToken generateToken(UserEntity user);
}

