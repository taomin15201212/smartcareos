#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 2 || "$1" != "--confirm" ]]; then
  echo "usage: $0 --confirm /absolute/path/to/backup.mv.db" >&2; exit 1
fi
source_file="$2"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
database_file="$project_dir/data/smartcareos.mv.db"
if [[ "$source_file" != /* || ! -f "$source_file" ]]; then echo "backup must be an existing absolute path" >&2; exit 1; fi
if command -v lsof >/dev/null && lsof "$database_file" >/dev/null 2>&1; then
  echo "refusing restore while H2 is open; stop SmartCareOS first" >&2; exit 2
fi
recovery="$database_file.before-restore-$(date -u +%Y%m%dT%H%M%SZ)"
[[ ! -f "$database_file" ]] || cp -p "$database_file" "$recovery"
cp -p "$source_file" "$database_file"
echo "restored $source_file; previous database: $recovery"
