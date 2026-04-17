package com.JobProcessingPipeline.JobProcessingPipeline.job.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobRulesTest {

    @Test
    void canBeClaimed_onlyPending() {
        assertTrue(JobRules.canBeClaimed(JobStatus.PENDING));
        assertFalse(JobRules.canBeClaimed(JobStatus.PROCESSING));
        assertFalse(JobRules.canBeClaimed(JobStatus.SUCCEEDED));
        assertFalse(JobRules.canBeClaimed(JobStatus.FAILED));
        assertFalse(JobRules.canBeClaimed(null));
    }

    @Test
    void canBeRetried_onlyFailed() {
        assertTrue(JobRules.canBeRetried(JobStatus.FAILED));
        assertFalse(JobRules.canBeRetried(JobStatus.PENDING));
        assertFalse(JobRules.canBeRetried(JobStatus.PROCESSING));
        assertFalse(JobRules.canBeRetried(JobStatus.SUCCEEDED));
        assertFalse(JobRules.canBeRetried(null));
    }

    @Test
    void canTransitionTo_exhaustiveMatrix() {
        Set<Transition> valid = Set.of(
                new Transition(JobStatus.PENDING, JobStatus.PROCESSING),
                new Transition(JobStatus.PROCESSING, JobStatus.SUCCEEDED),
                new Transition(JobStatus.PROCESSING, JobStatus.FAILED),
                new Transition(JobStatus.PROCESSING, JobStatus.PENDING),
                new Transition(JobStatus.FAILED, JobStatus.PENDING)
        );

        for (JobStatus from : JobStatus.values()) {
            for (JobStatus to : JobStatus.values()) {
                boolean expected = valid.contains(new Transition(from, to));
                boolean actual = JobRules.canTransitionTo(from, to);
                assertEquals(expected, actual, () -> "Transition mismatch for " + from + " -> " + to);
            }
        }
    }

    @Test
    void canTransitionTo_rejectsNulls() {
        assertFalse(JobRules.canTransitionTo(null, JobStatus.PENDING));
        assertFalse(JobRules.canTransitionTo(JobStatus.PENDING, null));
        assertFalse(JobRules.canTransitionTo(null, null));
    }

    @Test
    void assertCanTransition_throwsOnInvalidTransition() {
        assertThrows(IllegalStateException.class,
                () -> JobRules.assertCanTransition(JobStatus.SUCCEEDED, JobStatus.PROCESSING));
    }

    @Test
    void assertCanTransition_allowsValidTransition() {
        JobRules.assertCanTransition(JobStatus.PROCESSING, JobStatus.SUCCEEDED);
    }

    private record Transition(JobStatus from, JobStatus to) {
    }
}

