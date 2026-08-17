#!/usr/bin/env bash
set -euo pipefail
base_url="${SMARTCAREOS_BASE_URL:-http://127.0.0.1:8080}"
requests="${SMARTCAREOS_PERF_REQUESTS:-200}"
concurrency="${SMARTCAREOS_PERF_CONCURRENCY:-10}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
seq "$requests" | xargs -P "$concurrency" -I{} curl --max-time 10 -fsS -o /dev/null \
  -w '%{time_total}\n' "$base_url/api/v1/system/health" > "$work_dir/times"
awk -v total="$requests" '
{a[NR]=$1; sum+=$1} END {
  for(i=1;i<=NR;i++) for(j=i+1;j<=NR;j++) if(a[i]>a[j]) {t=a[i];a[i]=a[j];a[j]=t}
  p95=a[int(NR*.95)<1?1:int(NR*.95)];
  printf "requests=%d average_ms=%.2f p95_ms=%.2f max_ms=%.2f\n",NR,(sum/NR)*1000,p95*1000,a[NR]*1000
}' "$work_dir/times"
