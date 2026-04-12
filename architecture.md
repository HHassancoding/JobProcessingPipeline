# PipelineOps — Architecture

## Overview

PipelineOps is a backend job-processing platform built with Java and Spring Boot. Users submit JSON ingestion jobs through a REST API. Those jobs are stored in PostgreSQL, processed asynchronously by a background worker, and tracked through a clear lifecycle of states. The system is designed as a modular monolith — simple to build and deploy now, with clean boundaries that allow the worker and API to be separated later if scaling demands it.

---

## Architecture Style

**Modular Monolith (v1)**

One Spring Boot application contains:
- the REST API,
- the background worker,
- the job processing logic,
- the database access layer.

This is intentional. The architecture keeps deployment simple and development fast while preserving clean internal module boundaries that make future extraction straightforward.

---

## System Diagram

```
Client (HTTP)
    │
    ▼
┌─────────────────────────────┐
│     Spring Boot API          │
│                             │
│  ┌─────────┐  ┌──────────┐  │
│  │  Auth   │  │   Job    │  │
│  │  (JWT)  │  │   API    │  │
│  └─────────┘  └──────────┘  │
│                             │
│  ┌──────────────────────┐   │
│  │    Job Service       │   │
│  │  (submit/claim/retry)│   │
│  └──────────────────────┘   │
│                             │
│  ┌──────────────────────┐   │
│  │  Background Worker   │   │
│  │  (@Scheduled)        │   │
│  └──────────────────────┘   │
│                             │
│  ┌──────────────────────┐   │
│  │  Recovery Scheduler  │   │
│  │  (stuck job detect)  │   │
│  └──────────────────────┘   │
└───────────────┬─────────────┘
                │
                ▼
    ┌───────────────────────┐
    │      PostgreSQL        │
    │                       │
    │  jobs                 │
    │  job_attempts         │
    │  job_events           │
    │  users                │
    └───────────────────────┘
```

---

## Request Flow

### Job Submission
```
Client
  → POST /api/jobs
  → JWT verified by Spring Security
  → JobCommandService.createJob()
  → Job stored as PENDING in PostgreSQL
  → 201 Created with jobId returned
```

### Job Processing
```
@Scheduled Worker (every 5 seconds)
  → JobClaimService.claimNextJob()
  → SELECT ... FOR UPDATE SKIP LOCKED  (PostgreSQL atomic claim)
  → Job updated to PROCESSING
  → JsonIngestProcessor runs
  → Job updated to SUCCEEDED or FAILED
  → Result summary stored
```

### Stuck Job Recovery
```
@Scheduled Recovery (every 60 seconds)
  → Find jobs WHERE status = PROCESSING
    AND locked_until < NOW()
  → Requeue to PENDING if attempt_count < max_attempts
  → Mark FAILED if attempt limit reached
```

### Manual Retry
```
Client
  → POST /api/jobs/{id}/retry
  → JWT verified
  → JobCommandService.retryJob()
  → Validates job is in FAILED state
  → Resets to PENDING
```

---

## Job Lifecycle (State Machine)

```
                    ┌─────────┐
         submitted  │         │
         ──────────►│ PENDING │
                    │         │
                    └────┬────┘
                         │ worker claims
                         ▼
                    ┌────────────┐
                    │            │
                    │ PROCESSING │
                    │            │
                    └─┬────────┬─┘
                      │        │
          success      │        │  failure
                      ▼        ▼
              ┌──────────┐  ┌────────┐
              │SUCCEEDED │  │ FAILED │
              └──────────┘  └───┬────┘
                                │ manual retry
                                ▼
                           ┌─────────┐
                           │ PENDING │
                           └─────────┘

Special case:
  PROCESSING → PENDING  (lease expired, stuck job requeued)
```

### Transition Rules

| From | To | Trigger |
|---|---|---|
| `PENDING` | `PROCESSING` | Worker atomically claims job |
| `PROCESSING` | `SUCCEEDED` | Worker finishes successfully |
| `PROCESSING` | `FAILED` | Worker encounters unrecoverable error |
| `FAILED` | `PENDING` | User manually retries |
| `PROCESSING` | `PENDING` | Lease expires, recovery requeues job |

---

## Job Type

### `JSON_INGEST`

A user submits a JSON payload. The worker validates and normalizes the content and stores a result summary.

**Processing steps:**
1. Parse JSON safely.
2. Validate required fields are present.
3. Normalize values — trim strings, standardize dates, lowercase emails.
4. Count valid and invalid records.
5. Store result summary.
6. Mark job `SUCCEEDED` or `FAILED`.

**Fails when:**
- JSON is malformed and cannot be parsed.
- Required fields are missing.
- Record count is zero.

---

## Database Schema

### `jobs`

| Column | Type | Description |
|---|---|---|
| `id` | UUID | Primary key |
| `user_id` | UUID | Submitting user |
| `job_type` | VARCHAR | e.g. `JSON_INGEST` |
| `status` | VARCHAR | Current state |
| `payload_json` | JSONB | Input payload |
| `result_summary` | JSONB | Processing output |
| `attempt_count` | INT | How many times attempted |
| `max_attempts` | INT | Retry limit |
| `claimed_by` | VARCHAR | Worker instance ID |
| `locked_until` | TIMESTAMP | Lease expiry |
| `failure_reason` | TEXT | Last error message |
| `created_at` | TIMESTAMP | Submission time |
| `started_at` | TIMESTAMP | When processing began |
| `finished_at` | TIMESTAMP | When processing ended |

### `job_attempts`

| Column | Type | Description |
|---|---|---|
| `id` | UUID | Primary key |
| `job_id` | UUID | Parent job |
| `attempt_number` | INT | Attempt index |
| `outcome` | VARCHAR | `SUCCEEDED` or `FAILED` |
| `failure_reason` | TEXT | Error if failed |
| `started_at` | TIMESTAMP | Attempt start |
| `finished_at` | TIMESTAMP | Attempt end |
| `duration_ms` | BIGINT | Processing time |

### `job_events`

| Column | Type | Description |
|---|---|---|
| `id` | UUID | Primary key |
| `job_id` | UUID | Parent job |
| `event_type` | VARCHAR | e.g. `STATE_TRANSITION` |
| `from_status` | VARCHAR | Previous state |
| `to_status` | VARCHAR | New state |
| `message` | TEXT | Context message |
| `created_at` | TIMESTAMP | Event time |

---

## Queue Design

PostgreSQL is used as the job queue using `FOR UPDATE SKIP LOCKED`.

**Why this approach:**
- No additional infrastructure required.
- Atomic job claiming prevents duplicate processing.
- Easy to inspect job state directly via SQL.
- Appropriate for small-to-medium job volume.

**Claim query:**
```sql
SELECT id FROM jobs
WHERE status = 'PENDING'
AND next_run_at <= NOW()
ORDER BY created_at ASC
LIMIT 1
FOR UPDATE SKIP LOCKED;
```

**How it prevents duplicate processing:**
- `FOR UPDATE` locks the selected row.
- `SKIP LOCKED` makes other workers skip already-locked rows.
- Only one worker receives and processes each job.

---

## Package Structure

```
com.pipelineops
  ├── auth/
  │     ├── api/          → AuthController, LoginRequest, TokenResponse
  │     ├── application/  → AuthService
  │     ├── domain/       → UserRole
  │     ├── infra/        → UserEntity, UserRepository
  │     └── config/       → SecurityConfig, JwtService
  │
  ├── job/
  │     ├── api/          → JobController, CreateJobRequest, JobResponse
  │     ├── application/  → JobCommandService, JobQueryService,
  │     │                    JobClaimService, JobWorkerService,
  │     │                    StuckJobRecoveryService
  │     ├── domain/       → JobStatus, JobType, RetryPolicy, JobRules
  │     ├── infra/        → JobEntity, JobRepository, JobAttemptRepository,
  │     │                    JobClaimRepositoryCustom
  │     └── processor/    → JsonIngestProcessor
  │
  ├── ops/
  │     ├── api/          → OpsController, StuckJobResponse
  │     └── application/  → OpsService
  │
  └── common/             → shared DTOs, exceptions, base entities
```

---

## API Endpoints

### Auth
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register new user |
| `POST` | `/api/auth/login` | Authenticate and receive JWT |

### Jobs (User)
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/jobs` | Submit a new job |
| `GET` | `/api/jobs` | List your jobs |
| `GET` | `/api/jobs/{id}` | Get job status and result |
| `POST` | `/api/jobs/{id}/retry` | Manually retry a failed job |

### Ops (Admin)
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/ops/jobs/stuck` | List stuck jobs |
| `GET` | `/api/ops/jobs/{id}/attempts` | View attempt history |
| `GET` | `/api/ops/metrics` | Summary: counts by status, retry totals |
| `GET` | `/actuator/health` | Spring Boot health check |

---

## Authentication

- JWT-based stateless authentication via Spring Security.
- All `/api/jobs` endpoints require a valid Bearer token.
- All `/api/ops` endpoints require `ROLE_ADMIN`.
- Token is issued on login and verified on every request via a JWT filter.

---

## Operational Design

### Stuck Job Detection
- Every 60 seconds, the recovery scheduler queries for jobs in `PROCESSING` state where `locked_until` has passed.
- These jobs are considered abandoned — the worker may have crashed or timed out.
- If `attempt_count < max_attempts`, the job is requeued to `PENDING`.
- If at the limit, the job is marked `FAILED` with reason `LEASE_EXPIRED_MAX_ATTEMPTS`.

### Observability
The `/api/ops/metrics` endpoint exposes:
- job counts by status,
- average processing duration,
- retry count totals,
- oldest pending job age,
- stuck job count.

---

## Scaling Path

This is a v1 modular monolith. Future scaling can follow these steps without rewriting core logic:

| Step | Change | When needed |
|---|---|---|
| v1 | API + Worker in one app | Now |
| v2 | Extract worker into separate deployable | When processing load grows |
| v3 | Replace Postgres queue with dedicated broker | When job volume exceeds DB queue limits |
| v4 | Scale worker instances independently | When parallel throughput is needed |

The clean module boundaries in v1 make these steps possible without major redesign.

---

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Database | PostgreSQL |
| Auth | JWT via Spring Security |
| API Docs | Swagger / OpenAPI (springdoc) |
| Scheduling | Spring `@Scheduled` |
| Build | Maven |
| Deployment | Railway / Render (hobby tier) |
