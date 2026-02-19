#!/usr/bin/env bash
# Verifies all services in the hospital stack are healthy.
# Usage: bash scripts/healthcheck.sh

set -uo pipefail

PASS=0
FAIL=0

check() {
    local name="$1"
    local cmd="$2"
    if eval "${cmd}" &>/dev/null; then
        echo "✅ ${name}: healthy"
        ((PASS++))
    else
        echo "❌ ${name}: UNHEALTHY"
        ((FAIL++))
    fi
}

echo "── Hospital Patient Module Health Check ──────────────────────────────"

check "PostgreSQL" \
    "docker compose exec -T db pg_isready -U patient_app -d hospital_patients"

check "Backend liveness" \
    "curl -sf http://localhost:8080/actuator/health/liveness"

check "Backend readiness" \
    "curl -sf http://localhost:8080/actuator/health/readiness"

check "Nginx HTTPS" \
    "curl -sfk https://localhost/ --max-time 5"

check "Frontend serving" \
    "curl -sfk https://localhost/ --max-time 5 | grep -q 'Hospital'"

echo "──────────────────────────────────────────────────────────────────────"
echo "Result: ${PASS} healthy, ${FAIL} unhealthy"

if [[ "${FAIL}" -gt 0 ]]; then
    exit 1
fi
