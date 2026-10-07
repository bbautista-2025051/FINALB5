#!/bin/bash
set -euo pipefail

# Ejecuta todos los microservicios en local (sin Docker) contra el MySQL local.
# La aplicación ya trae valores por defecto para desarrollo, así que .env es opcional.

if [ -f .env ]; then
    echo "Cargando variables desde .env"
    while IFS='=' read -r clave valor || [ -n "$clave" ]; do
        case "$clave" in
            ''|\#*) continue ;;
        esac
        clave="$(echo "$clave" | tr -d '\r')"
        valor="$(echo "$valor" | tr -d '\r')"
        export "$clave=$valor"
    done < .env
fi

# En modo local todos los servicios viven en localhost.
export DB_HOST="${DB_HOST:-localhost}"
export AUTH_SERVICE_URI="${AUTH_SERVICE_URI:-http://localhost:8081}"
export USER_SERVICE_URI="${USER_SERVICE_URI:-http://localhost:8082}"
export MASCOTA_SERVICE_URI="${MASCOTA_SERVICE_URI:-http://localhost:8083}"
export CITA_SERVICE_URI="${CITA_SERVICE_URI:-http://localhost:8084}"
export EXPEDIENTE_SERVICE_URI="${EXPEDIENTE_SERVICE_URI:-http://localhost:8085}"

# Usa el wrapper de Maven si está disponible; si no, el mvn del PATH.
if [ -x ./mvnw ]; then
    MVN="./mvnw"
else
    MVN="mvn"
fi

echo "=== Construyendo proyecto ==="
$MVN -B clean install -DskipTests

echo ""
echo "=== Iniciando servicios ==="
echo "Gateway: http://localhost:8090"
echo ""

java -jar user-service/target/user-service-1.0.0-SNAPSHOT.jar &
USER_PID=$!
echo "user-service iniciado (PID: $USER_PID) en puerto 8082"

sleep 5

java -jar auth-service/target/auth-service-1.0.0-SNAPSHOT.jar &
AUTH_PID=$!
echo "auth-service iniciado (PID: $AUTH_PID) en puerto 8081"

java -jar mascota-service/target/mascota-service-1.0.0-SNAPSHOT.jar &
MASCOTA_PID=$!
echo "mascota-service iniciado (PID: $MASCOTA_PID) en puerto 8083"

java -jar cita-service/target/cita-service-1.0.0-SNAPSHOT.jar &
CITA_PID=$!
echo "cita-service iniciado (PID: $CITA_PID) en puerto 8084"

java -jar expediente-service/target/expediente-service-1.0.0-SNAPSHOT.jar &
EXPEDIENTE_PID=$!
echo "expediente-service iniciado (PID: $EXPEDIENTE_PID) en puerto 8085"

java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar &
GATEWAY_PID=$!
echo "api-gateway iniciado (PID: $GATEWAY_PID) en puerto 8090"

detener() {
    echo ""
    echo "Deteniendo servicios..."
    kill "$USER_PID" "$AUTH_PID" "$MASCOTA_PID" "$CITA_PID" "$EXPEDIENTE_PID" "$GATEWAY_PID" 2>/dev/null || true
}
trap detener INT TERM

echo ""
echo "=== Todos los servicios iniciados ==="
echo "Para detener: kill $USER_PID $AUTH_PID $MASCOTA_PID $CITA_PID $EXPEDIENTE_PID $GATEWAY_PID"
echo ""
echo "Presiona Ctrl+C para detener todos los servicios"
wait
