#!/usr/bin/env bash
set -euo pipefail
container="${SMARTCAREOS_MYSQL_CONTAINER:-smartcareos-integration-mysql-1}"
drill_db="smartcareos_restore_drill"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
started="$(date +%s)"
docker exec "$container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --single-transaction -uroot smartcareos' > "$work_dir/backup.sql"
test -s "$work_dir/backup.sql"
source_count="$(docker exec "$container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=\"smartcareos\""')"
docker exec "$container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "DROP DATABASE IF EXISTS smartcareos_restore_drill; CREATE DATABASE smartcareos_restore_drill CHARACTER SET utf8mb4"'
docker exec -i "$container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot smartcareos_restore_drill' < "$work_dir/backup.sql"
restored_count="$(docker exec "$container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=\"smartcareos_restore_drill\""')"
schema_version="$(docker exec "$container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot smartcareos_restore_drill -e "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1"')"
if [[ "$source_count" != "$restored_count" ]]; then echo "table count mismatch" >&2; exit 1; fi
docker exec "$container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "DROP DATABASE smartcareos_restore_drill"'
ended="$(date +%s)"
echo "restore_drill=PASS tables=$restored_count schema=$schema_version rto_seconds=$((ended-started)) rpo_seconds=0"
