# Auth Module — Quickstart Guide

**Branch**: `002-auth-module` | **Date**: 2026-02-20

---

## Prerequisites

- Docker Desktop running
- Existing `.env` file at project root (from Module 1 setup)
- New required env vars added to `.env` (see below)

---

## New Environment Variables

Add the following to your `.env` file before starting:

```bash
# ── Auth Module (required) ────────────────────────────────────────────────────
ADMIN_USERNAME=admin
ADMIN_INITIAL_PASSWORD=<choose-a-strong-password>   # REQUIRED — no default

# ── Token expiry (optional — defaults shown) ──────────────────────────────────
APP_JWT_EXPIRATION_HOURS=8
APP_JWT_REFRESH_EXPIRATION_HOURS=24

# ── Account lockout (optional — defaults shown) ───────────────────────────────
AUTH_LOCKOUT_MAX_ATTEMPTS=5
AUTH_LOCKOUT_DURATION_MINUTES=15
```

> **ADMIN_INITIAL_PASSWORD is mandatory.** If absent, the application refuses to start.

---

## Start the Stack

```bash
# From project root
docker compose up --build -d

# Watch backend startup logs
docker logs hospital-backend -f
```

**Healthy output includes**:
```
INFO  Flyway: Successfully applied 7 migrations (V1–V7)
INFO  AdminSeeder: Seed ADMIN account created for username: admin
INFO  Started HospitalApplication in X.XXX seconds
```

---

## First Login

```bash
curl -s -k -X POST https://localhost/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<your-ADMIN_INITIAL_PASSWORD>"}' | jq .
```

**Expected response**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "U2026001",
  "username": "admin",
  "role": "ADMIN",
  "expiresAt": "2026-02-20T21:00:00Z"
}
```

Store the token for subsequent requests:
```bash
TOKEN=$(curl -s -k -X POST https://localhost/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<password>"}' | jq -r '.token')
```

---

## Create a Staff Account

```bash
# Create a receptionist (ADMIN token required)
curl -s -k -X POST https://localhost/api/v1/admin/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "receptionist1",
    "password": "R3c3pt10n@",
    "role": "RECEPTIONIST",
    "email": "alice@hospital.local",
    "department": "Admissions"
  }' | jq .
```

---

## Log In as the New Staff Member

```bash
RECEPT_TOKEN=$(curl -s -k -X POST https://localhost/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"receptionist1","password":"R3c3pt10n@"}' | jq -r '.token')

# Access patient list using the new token
curl -s -k https://localhost/api/v1/patients \
  -H "Authorization: Bearer $RECEPT_TOKEN" | jq .
```

---

## Token Refresh

```bash
curl -s -k -X POST https://localhost/api/v1/auth/refresh \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

## Logout

```bash
curl -s -k -X POST https://localhost/api/v1/auth/logout \
  -H "Authorization: Bearer $TOKEN"
# Returns: 204 No Content

# Verify token is revoked
curl -s -k https://localhost/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN"
# Returns: 401 Unauthorized
```

---

## Current User Profile

```bash
curl -s -k https://localhost/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

## List Staff Accounts

```bash
# All staff (ADMIN only)
curl -s -k "https://localhost/api/v1/admin/users?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Filter by role
curl -s -k "https://localhost/api/v1/admin/users?role=DOCTOR" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

## Remove the Dev Auth Stub

The `DevAuthController` (`/api/v1/auth/dev-login`) is removed in this module.
Any existing client code using `/dev-login` must switch to `/api/v1/auth/login`.

**Frontend update**: The login form POSTs to `/api/v1/auth/login` (was `/api/v1/auth/dev-login`).

---

## Run Tests

```bash
# Unit tests
cd backend && mvn test

# Integration tests (requires Docker for Testcontainers)
cd backend && mvn verify -Pfailsafe
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| App refuses to start: `ADMIN_INITIAL_PASSWORD` | Missing env var | Add `ADMIN_INITIAL_PASSWORD=<value>` to `.env` |
| Login returns 401 after stack rebuild | Admin seeder skipped (users exist) | If DB was wiped: `docker compose down -v && docker compose up --build -d` |
| Login returns 423 | Account locked (5 failed attempts) | Wait 15 minutes, or reduce `AUTH_LOCKOUT_DURATION_MINUTES` in `.env` for dev |
| `401` on every request after logout | Token revoked correctly | Obtain a new token via `POST /auth/login` |
| Flyway checksum error on V1–V4 | Existing migrations modified | Never edit V1–V4 migrations; restore from git |
