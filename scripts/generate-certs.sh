#!/usr/bin/env bash
# Generates self-signed TLS certificates for local HTTPS development.
# Output: nginx/ssl/cert.pem and nginx/ssl/key.pem
# Usage: bash scripts/generate-certs.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SSL_DIR="${REPO_ROOT}/nginx/ssl"

echo "Generating self-signed TLS certificates for localhost..."
mkdir -p "${SSL_DIR}"

openssl req -x509 -nodes -days 365 \
    -newkey rsa:2048 \
    -keyout "${SSL_DIR}/key.pem" \
    -out "${SSL_DIR}/cert.pem" \
    -subj "/C=US/ST=Dev/L=Local/O=Hospital/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
    2>/dev/null

echo "✅ Certificates generated:"
echo "   ${SSL_DIR}/cert.pem"
echo "   ${SSL_DIR}/key.pem"
echo ""
echo "These files are gitignored. Regenerate if the cert expires (365 days)."
