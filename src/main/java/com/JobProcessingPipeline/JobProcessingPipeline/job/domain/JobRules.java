package com.JobProcessingPipeline.JobProcessingPipeline.job.domain;

public final class JobRules {

    private JobRules() {
    }

    public static boolean canBeClaimed(JobStatus status) {
        return status == JobStatus.PENDING;
    }

    public static boolean canBeRetried(JobStatus status) {
        return status == JobStatus.FAILED;
    }

    public static boolean canTransitionTo(JobStatus from, JobStatus to) {
        if (from == null || to == null) {
            return false;
        }

        return switch (from) {
            case PENDING -> to == JobStatus.PROCESSING;
            case PROCESSING -> to == JobStatus.SUCCEEDED
                    || to == JobStatus.FAILED
                    || to == JobStatus.PENDING;
            case FAILED -> to == JobStatus.PENDING;
            case SUCCEEDED -> false;
        };
    }

    public static void assertCanTransition(JobStatus from, JobStatus to) {
        if (!canTransitionTo(from, to)) {
            throw new IllegalStateException("Invalid transition: " + from + " -> " + to);
        }
    }
}

