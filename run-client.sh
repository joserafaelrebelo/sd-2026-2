#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

GRPC_HOST="${GRPC_SERVER_HOST:-127.0.0.1}"
GRPC_PORT="${GRPC_SERVER_PORT:-50051}"
WEB_PORT="${WEB_PORT:-8080}"
WEB_HOST="${WEB_BIND_ADDRESS:-0.0.0.0}"

# Se não for execução em modo de teste estrito, verificar porta web
IS_TEST=false
for arg in "$@"; do
  if [ "$arg" = "--test" ]; then
    IS_TEST=true
    break
  fi
done

if [ "$IS_TEST" = false ]; then
  if lsof -ti:"$WEB_PORT" >/dev/null 2>&1; then
    echo "[run-client.sh] Liberando porta $WEB_PORT ocupada por processo anterior..."
    kill $(lsof -ti:"$WEB_PORT") 2>/dev/null || true
    sleep 0.5
  fi
fi

# Compilar JAR do cliente se não existir
JAR_FILE="service-a-client/target/service-a-client-0.1.0.jar"
if [ ! -f "$JAR_FILE" ]; then
  echo "[run-client.sh] Compilando service-a-client..."
  mvn -q -f service-a-client/pom.xml clean package
fi

echo "[run-client.sh] Executando Cliente A (Alvo gRPC: $GRPC_HOST:$GRPC_PORT)..."
exec java -jar "$JAR_FILE" "$@"

