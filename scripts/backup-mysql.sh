#!/usr/bin/env bash
set -euo pipefail
: "${SMARTCAREOS_DB_HOST:?set SMARTCAREOS_DB_HOST}"
: "${SMARTCAREOS_DB_USERNAME:?set SMARTCAREOS_DB_USERNAME}"
: "${SMARTCAREOS_DB_PASSWORD:?set SMARTCAREOS_DB_PASSWORD}"
database="${SMARTCAREOS_DB_NAME:-smartcareos}"
backup_dir="${SMARTCAREOS_BACKUP_DIR:-./backups}"
mkdir -p "$backup_dir"
target="$backup_dir/smartcareos-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
MYSQL_PWD="$SMARTCAREOS_DB_PASSWORD" mysqldump --single-transaction --routines --triggers \
  -h "$SMARTCAREOS_DB_HOST" -u "$SMARTCAREOS_DB_USERNAME" "$database" | gzip -9 > "$target"
shasum -a 256 "$target" > "$target.sha256"
echo "$target"
