package com.JobProcessingPipeline.JobProcessingPipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JobProcessingPipelineApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobProcessingPipelineApplication.class, args);
	}

}
