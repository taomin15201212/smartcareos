#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 2 || "$1" != "--confirm" ]]; then
  echo "usage: $0 --confirm /absolute/path/to/backup.sql.gz" >&2; exit 1
fi
: "${SMARTCAREOS_DB_HOST:?set SMARTCAREOS_DB_HOST}"
: "${SMARTCAREOS_DB_USERNAME:?set SMARTCAREOS_DB_USERNAME}"
: "${SMARTCAREOS_DB_PASSWORD:?set SMARTCAREOS_DB_PASSWORD}"
source_file="$2"
database="${SMARTCAREOS_DB_NAME:-smartcareos}"
if [[ "$source_file" != /* || ! -f "$source_file" ]]; then echo "backup must be an existing absolute path" >&2; exit 1; fi
echo "restoring into $SMARTCAREOS_DB_HOST/$database" >&2
gzip -dc "$source_file" | MYSQL_PWD="$SMARTCAREOS_DB_PASSWORD" mysql \
  -h "$SMARTCAREOS_DB_HOST" -u "$SMARTCAREOS_DB_USERNAME" "$database"
echo "restore completed"
