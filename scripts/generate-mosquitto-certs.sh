#!/usr/bin/env bash
set -euo pipefail

: "${SMARTCAREOS_MQTT_KEYSTORE_PASSWORD:?set SMARTCAREOS_MQTT_KEYSTORE_PASSWORD}"

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target="$project_dir/deploy/mosquitto/certs"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

if [[ -e "$target/server.key" && "${SMARTCAREOS_MQTT_ROTATE_CERTS:-false}" != "true" ]]; then
  echo "refusing to overwrite existing MQTT certificates; set SMARTCAREOS_MQTT_ROTATE_CERTS=true for an intentional rotation" >&2
  exit 1
fi

mkdir -p "$target"

openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 3650 \
  -keyout "$work_dir/ca.key" -out "$work_dir/ca.crt" \
  -subj "/CN=SmartCareOS Local Integration CA"

openssl req -newkey rsa:2048 -sha256 -nodes \
  -keyout "$work_dir/server.key" -out "$work_dir/server.csr" \
  -subj "/CN=mosquitto" \
  -addext "subjectAltName=DNS:mosquitto,DNS:localhost,IP:127.0.0.1"
openssl x509 -req -sha256 -days 825 -in "$work_dir/server.csr" \
  -CA "$work_dir/ca.crt" -CAkey "$work_dir/ca.key" -CAcreateserial \
  -copy_extensions copy -out "$work_dir/server.crt"

create_client() {
  local name="$1"
  openssl req -newkey rsa:2048 -sha256 -nodes \
    -keyout "$work_dir/$name.key" -out "$work_dir/$name.csr" \
    -subj "/CN=$name"
  openssl x509 -req -sha256 -days 825 -in "$work_dir/$name.csr" \
    -CA "$work_dir/ca.crt" -CAkey "$work_dir/ca.key" -CAcreateserial \
    -out "$work_dir/$name.crt"
}

create_client smartcareos-app
create_client device-button-e2e-001

openssl pkcs12 -export -name smartcareos-app \
  -inkey "$work_dir/smartcareos-app.key" -in "$work_dir/smartcareos-app.crt" \
  -certfile "$work_dir/ca.crt" -out "$work_dir/app-keystore.p12" \
  -passout env:SMARTCAREOS_MQTT_KEYSTORE_PASSWORD

keytool -importcert -noprompt -alias smartcareos-local-ca \
  -file "$work_dir/ca.crt" -keystore "$work_dir/truststore.p12" \
  -storetype PKCS12 -storepass "$SMARTCAREOS_MQTT_KEYSTORE_PASSWORD"

cp "$work_dir/ca.crt" "$target/ca.crt"
cp "$work_dir/server.crt" "$target/server.crt"
cp "$work_dir/server.key" "$target/server.key"
cp "$work_dir/smartcareos-app.crt" "$target/app.crt"
cp "$work_dir/smartcareos-app.key" "$target/app.key"
cp "$work_dir/device-button-e2e-001.crt" "$target/device-button-e2e-001.crt"
cp "$work_dir/device-button-e2e-001.key" "$target/device-button-e2e-001.key"
cp "$work_dir/app-keystore.p12" "$target/app-keystore.p12"
cp "$work_dir/truststore.p12" "$target/truststore.p12"

chmod 644 "$target"/*.crt
chmod 600 "$target"/*.key "$target"/*.p12
printf '%s\n' "$target"
