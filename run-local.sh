#!/bin/bash

if [ ! -f .env ]; then
    echo "ERROR: Archivo .env no encontrado."
    echo "Copia .env.example a .env y configura las variables:"
    echo "  cp .env.example .env"
    exit 1
fi

export $(grep -v '^#' .env | xargs)

echo "=== Construyendo proyecto ==="
mvn -B clean install -DskipTests

echo ""
echo "=== Iniciando servicios ==="
echo "Los servicios se iniciarán en segundo plano."
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

echo ""
echo "=== Todos los servicios iniciados ==="
echo "Para detener: kill $USER_PID $AUTH_PID $MASCOTA_PID $CITA_PID $EXPEDIENTE_PID $GATEWAY_PID"
echo ""
echo "Presiona Ctrl+C para detener todos los servicios"
wait
