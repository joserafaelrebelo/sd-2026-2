#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

mkdir -p data

PORT="${GRPC_SERVER_PORT:-50051}"
HOST="${GRPC_SERVER_BIND_ADDRESS:-0.0.0.0}"
DB_PATH="${RATEIO_DB_PATH:-data/rateio.db}"

echo "========================================================="
echo "       INICIANDO SERVIDOR B (RATEIO - gRPC + SQLite)     "
echo "========================================================="
echo "  Endereço de escuta (Bind): $HOST"
echo "  Porta gRPC:                $PORT"
echo "  Banco de Dados SQLite:     $DB_PATH"
echo "========================================================="

# Encerrar qualquer processo anterior na mesma porta gRPC
if lsof -ti:"$PORT" >/dev/null 2>&1; then
  echo "[run-server.sh] Liberando porta $PORT ocupada por processo anterior..."
  kill $(lsof -ti:"$PORT") 2>/dev/null || true
  sleep 0.5
fi

# Compilar JAR do servidor se não existir
JAR_FILE="service-b-server/target/service-b-server-0.1.0.jar"
if [ ! -f "$JAR_FILE" ]; then
  echo "[run-server.sh] Compilando service-b-server..."
  mvn -q -f service-b-server/pom.xml clean package
fi

echo "[run-server.sh] Executando Servidor B..."
exec java -jar "$JAR_FILE" "$@"

