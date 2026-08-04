# JobProcessingPipeline

Minimal Spring Boot auth layer with JWT issuance.

## What is implemented

- `POST /api/auth/register`
- `POST /api/auth/login`
- BCrypt password hashing
- JWT token issuance
- duplicate-email handling
- validation for malformed/invalid input
- auth logging without passwords or full tokens

## Current security stance

Auth endpoints are public for now. This keeps the first pass minimal while leaving room to harden request authorization later.

## Run tests

```powershell
.\mvnw.cmd test
```

## Auth endpoints

### Register

```http
POST /api/auth/register
Content-Type: application/json

{ "email": "string", "password": "string" }
```

Responses:
- `201 Created`
- `400 Bad Request` for invalid input
- `409 Conflict` for duplicate email

### Login

```http
POST /api/auth/login
Content-Type: application/json

{ "email": "string", "password": "string" }
```

Responses:
- `200 OK`
- `400 Bad Request` for invalid input
- `401 Unauthorized` for invalid credentials

Response body:

```json
{ "token": "string", "expiresIn": 3600 }
```

## JWT configuration

JWT settings live in `src/main/resources/application.properties`:

- `app.auth.jwt.issuer`
- `app.auth.jwt.secret-base64`
- `app.auth.jwt.expiration-seconds`

For production, override the secret with an environment-specific value.

