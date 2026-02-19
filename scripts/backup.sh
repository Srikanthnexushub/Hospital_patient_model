#!/usr/bin/env bash
# Creates a dated pg_dump backup of the patient database.
# Usage: bash scripts/backup.sh
# Output: backups/hospital_patients_YYYY-MM-DD_HH-MM.sql

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_DIR="${REPO_ROOT}/backups"
TIMESTAMP=$(date +%Y-%m-%d_%H-%M)
BACKUP_FILE="${BACKUP_DIR}/hospital_patients_${TIMESTAMP}.sql"

# Load env vars if .env exists
if [[ -f "${REPO_ROOT}/.env" ]]; then
    source "${REPO_ROOT}/.env"
fi

DB_NAME="${DB_NAME:-hospital_patients}"
DB_USER="${DB_USER:-patient_app}"

mkdir -p "${BACKUP_DIR}"

echo "Creating backup: ${BACKUP_FILE}"
docker compose -f "${REPO_ROOT}/docker-compose.yml" exec -T db \
    pg_dump -U "${DB_USER}" "${DB_NAME}" > "${BACKUP_FILE}"

SIZE=$(du -sh "${BACKUP_FILE}" | cut -f1)
echo "✅ Backup complete: ${BACKUP_FILE} (${SIZE})"
