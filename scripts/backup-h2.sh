#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
database_file="$project_dir/data/smartcareos.mv.db"
backup_dir="${SMARTCAREOS_BACKUP_DIR:-$project_dir/backups}"
if [[ ! -f "$database_file" ]]; then echo "database not found: $database_file" >&2; exit 1; fi
if command -v lsof >/dev/null && lsof "$database_file" >/dev/null 2>&1; then
  echo "refusing unsafe file copy while H2 is open; stop SmartCareOS first" >&2; exit 2
fi
mkdir -p "$backup_dir"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$backup_dir/smartcareos-$timestamp.mv.db"
cp -p "$database_file" "$target"
shasum -a 256 "$target" > "$target.sha256"
echo "$target"
