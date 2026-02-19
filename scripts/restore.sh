#!/usr/bin/env bash
# Restores a pg_dump backup file to the patient database.
# Usage: bash scripts/restore.sh backups/hospital_patients_2026-02-19_02-00.sql

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

BACKUP_FILE="${1:-}"
if [[ -z "${BACKUP_FILE}" ]]; then
    echo "❌ Usage: bash scripts/restore.sh <backup-file.sql>"
    exit 1
fi

if [[ ! -f "${BACKUP_FILE}" ]]; then
    echo "❌ Backup file not found: ${BACKUP_FILE}"
    exit 1
fi

# Load env vars if .env exists
if [[ -f "${REPO_ROOT}/.env" ]]; then
    source "${REPO_ROOT}/.env"
fi

DB_NAME="${DB_NAME:-hospital_patients}"
DB_USER="${DB_USER:-patient_app}"

echo "⚠️  WARNING: This will overwrite the current database '${DB_NAME}'."
read -r -p "Are you sure? (yes/no): " CONFIRM
if [[ "${CONFIRM}" != "yes" ]]; then
    echo "Restore cancelled."
    exit 0
fi

echo "Restoring from: ${BACKUP_FILE}"
docker compose -f "${REPO_ROOT}/docker-compose.yml" exec -T db \
    psql -U "${DB_USER}" -d "${DB_NAME}" < "${BACKUP_FILE}"

echo "✅ Restore complete."
