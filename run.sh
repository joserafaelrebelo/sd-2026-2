#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

mkdir -p data

# Kill any stale server on port 50051 or 8080
for PORT in 50051 8080; do
  if lsof -ti:$PORT >/dev/null 2>&1; then
    echo "[run.sh] Encerrando processo anterior na porta $PORT..."
    kill $(lsof -ti:$PORT) 2>/dev/null || true
    sleep 0.5
  fi
done

echo "[run.sh] Compilando módulos locais..."
mvn -q -f service-b-server/pom.xml clean package
mvn -q -f service-a-client/pom.xml clean package

echo "[run.sh] Iniciando servidor gRPC local em localhost:50051..."
java -jar service-b-server/target/service-b-server-0.1.0.jar > /tmp/rateio-server.log 2>&1 &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true; sleep 0.5; kill -9 "$SERVER_PID" 2>/dev/null || true' EXIT

for i in $(seq 1 30); do
  if ss -ltn | grep -q ':50051 '; then
    break
  fi
  sleep 1
done

if ! ss -ltn | grep -q ':50051 '; then
  echo "[run.sh] O servidor não iniciou corretamente em localhost:50051."
  cat /tmp/rateio-server.log || true
  exit 1
fi

echo "[run.sh] Iniciando cliente / Web Gateway..."
java -jar service-a-client/target/service-a-client-0.1.0.jar "$@"
