# Quickstart: Patient Module (Local Docker)

**Time to running**: ~3 minutes on first run, ~30 seconds on subsequent runs.

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Docker Desktop | 4.x+ | https://docs.docker.com/get-docker/ |
| Docker Compose | v2.x (bundled with Docker Desktop) | Included |
| Git | Any | https://git-scm.com/ |
| OpenSSL | Any (for cert generation) | Pre-installed on macOS/Linux |

No Java, Node, Maven, or PostgreSQL installations are required on the host machine.
Everything runs inside Docker.

---

## Step 1: Clone and Configure

```bash
# Clone the repository
git clone <repo-url>
cd Hospital_patient_model

# Copy the environment template
cp .env.example .env
```

Edit `.env` — at minimum set a strong `DB_PASSWORD` and `JWT_SECRET`. All other defaults
work for local development.

```env
# .env (local values — gitignored)
DB_HOST=db
DB_PORT=5432
DB_NAME=hospital_patients
DB_USER=patient_app
DB_PASSWORD=ChangeMe123!

SERVER_PORT=8080
JWT_SECRET=your-256-bit-secret-key-here
LOG_LEVEL=INFO

DB_POOL_MIN=5
DB_POOL_MAX=20

REACT_APP_API_BASE_URL=https://localhost/api/v1
NGINX_SERVER_NAME=localhost
```

---

## Step 2: Generate Self-Signed TLS Certificates

```bash
# Creates nginx/ssl/cert.pem and nginx/ssl/key.pem
bash scripts/generate-certs.sh
```

This runs a single `openssl req` command and places the certificates in `nginx/ssl/`
(gitignored). Required for HTTPS on `localhost`.

---

## Step 3: Start the Stack

```bash
docker compose up --build
```

On first run this will:
1. Build the backend JAR (Maven multi-stage Docker build) — ~2 minutes
2. Build the frontend (Vite production build) — ~30 seconds
3. Pull Nginx and PostgreSQL images
4. Start PostgreSQL, wait for it to be healthy
5. Start the backend (runs Flyway migrations automatically)
6. Start Nginx and the frontend

**Expected output when ready**:
```
db         | database system is ready to accept connections
backend    | Started PatientModuleApplication in 8.3 seconds
frontend   | /docker-entrypoint.sh: Configuration complete
proxy      | nginx: worker process started
```

---

## Step 4: Verify

| Check | URL | Expected |
|---|---|---|
| App (UI) | https://localhost | Patient list page |
| API health | https://localhost/api/v1/actuator/health | `{"status":"UP"}` |
| API readiness | https://localhost/api/v1/actuator/health/readiness | `{"status":"UP"}` |
| API docs (Swagger UI) | https://localhost/api/v1/swagger-ui.html | Interactive API docs |
| PgAdmin (optional) | http://localhost:5050 | PostgreSQL GUI |

> **Browser warning**: Your browser will warn about the self-signed certificate.
> Click "Advanced → Proceed to localhost" to continue. This is expected for local dev.

---

## Step 5: Make Your First API Call

```bash
# Get a JWT from your Auth Module (or use a test token)
TOKEN="your-jwt-here"

# Register a patient
curl -X POST https://localhost/api/v1/patients \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -k \
  -d '{
    "firstName": "Jane",
    "lastName": "Smith",
    "dateOfBirth": "1985-06-15",
    "gender": "FEMALE",
    "phone": "555-123-4567"
  }'

# Expected response:
# {"patientId":"P2026001","message":"Patient registered successfully. Patient ID: P2026001"}

# Search patients
curl -X GET "https://localhost/api/v1/patients?query=Smith&status=ACTIVE" \
  -H "Authorization: Bearer $TOKEN" \
  -k
```

---

## Common Commands

```bash
# Start in background
docker compose up -d

# View backend logs
docker compose logs -f backend

# View all logs
docker compose logs -f

# Stop (preserves data volumes)
docker compose down

# Stop and wipe all data (destructive)
docker compose down --volumes

# Rebuild a single service after code change
docker compose up --build backend

# Connect to PostgreSQL directly (dev only)
docker compose exec db psql -U patient_app -d hospital_patients

# Run backend unit tests (without Docker)
docker run --rm -v "$PWD/backend":/app -w /app maven:3.9-eclipse-temurin-17 \
  mvn test

# Run backend integration tests (requires PostgreSQL via Testcontainers)
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$PWD/backend":/app -w /app maven:3.9-eclipse-temurin-17 \
  mvn verify -Pfailsafe
```

---

## Manual Backup

```bash
# Create a dated backup dump
bash scripts/backup.sh

# Output: backups/hospital_patients_2026-02-19_02-00.sql
```

## Restore from Backup

```bash
# Restore a specific dump file
bash scripts/restore.sh backups/hospital_patients_2026-02-19_02-00.sql
```

---

## Health Check Script

```bash
# Verify all services are healthy
bash scripts/healthcheck.sh

# Expected output:
# ✅ PostgreSQL: healthy
# ✅ Backend liveness: UP
# ✅ Backend readiness: UP
# ✅ Nginx: responding on 443
# ✅ Frontend: serving
```

---

## Service URLs (Local)

| Service | URL | Notes |
|---|---|---|
| Frontend | https://localhost | React SPA |
| API (via Nginx) | https://localhost/api/v1 | All API calls go through Nginx |
| Swagger UI | https://localhost/api/v1/swagger-ui.html | Auto-generated from OpenAPI spec |
| Backend direct | http://localhost:8080 | Dev only — bypasses Nginx/TLS |
| PostgreSQL | localhost:5432 | Dev only — exposed for tooling access |
| PgAdmin | http://localhost:5050 | Dev only — optional service |

---

## Troubleshooting

| Problem | Likely Cause | Fix |
|---|---|---|
| Backend fails to start | DB not yet healthy | Wait 10 seconds and retry; check `docker compose logs db` |
| `certificate verify failed` in curl | Self-signed cert | Add `-k` flag to curl, or trust the cert in your browser |
| `Connection refused` on 443 | Nginx not started | Run `docker compose ps` and check proxy status |
| Flyway migration error | Schema already exists from previous run | Run `docker compose down --volumes` and restart |
| PgAdmin can't connect | Wrong host | Use `db` as hostname (Docker service name, not `localhost`) |
