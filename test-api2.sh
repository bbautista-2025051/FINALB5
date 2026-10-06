#!/bin/bash


# CONFIGURACIÓN GENERAL

BASE_URL="http://localhost:8090/api/v1"
ADMIN_EMAIL="admin@veterinaria.com"
ADMIN_PASS="Admin123!"
CLIENTE_EMAIL="dueno@veterinaria.com"
CLIENTE_PASS="Cliente123!"


echo "  INICIANDO PRUEBAS DE VETERINARIA"



# 1. REGISTRO Y AUTENTICACIÓN

echo -e "\n[1] Registrando usuario CLIENTE..."
curl -s -X POST "$BASE_URL/auth/registro" \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Pedro Armas",
    "telefono": "55551234",
    "email": "'"$CLIENTE_EMAIL"'",
    "password": "'"$CLIENTE_PASS"'"
  }' | jq .

echo -e "\n[2] Autenticando usuario ADMIN..."
ADMIN_LOGIN_RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "'"$ADMIN_EMAIL"'",
    "password": "'"$ADMIN_PASS"'"
  }')

ADMIN_TOKEN=$(echo $ADMIN_LOGIN_RESP | jq -r '.token // .accessToken')

if [ "$ADMIN_TOKEN" == "null" ] || [ -z "$ADMIN_TOKEN" ]; then
  echo "--> Error al obtener el token de ADMIN. Revisa credenciales o endpoint /auth/login."
  exit 1
fi

echo " Token Admin Obtenido: ${ADMIN_TOKEN:0:20}..."


# 2. CREACIÓN DE RECURSOS (Mascota)


echo -e "\n[3] Autenticando usuario CLIENTE..."
CLIENTE_LOGIN_RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "'"$CLIENTE_EMAIL"'",
    "password": "'"$CLIENTE_PASS"'"
  }')

CLIENTE_TOKEN=$(echo $CLIENTE_LOGIN_RESP | jq -r '.token // .accessToken')

echo -e "\n[4] Registrando Mascota (Rol CLIENTE)..."
MASCOTA_RESP=$(curl -s -X POST "$BASE_URL/mascotas" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CLIENTE_TOKEN" \
  -d '{
    "nombre": "Zafir",
    "especie": "PERRO",
    "raza": "Beagle",
    "edad": 3
  }')

echo $MASCOTA_RESP | jq .
MASCOTA_ID=$(echo $MASCOTA_RESP | jq -r '.id')


# 3. CONTROL DE ACCESO (403 Forbidden)


echo -e "\n[5] Intentando crear expediente con Rol CLIENTE (Debe fallar con 403 Forbidden)..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/expedientes" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CLIENTE_TOKEN" \
  -d '{
    "citaId": 1,
    "diagnostico": "Consulta general",
    "tratamiento": "Desparasitante",
    "pesoKg": 12.5
  }')

if [ "$HTTP_STATUS" -eq 403 ]; then
  echo "--> Seguridad Validada: Recibido Status 403 Forbidden correctamente."
else
  echo "-->️ Advertencia: Se esperaba 403 pero se obtuvo Status $HTTP_STATUS."
fi


# 4. PRUEBA DE ESTRÉS Y CONCURRENCIA


echo -e "\n"
echo " --> EJECUTANDO PRUEBA DE ESTRÉS EN CONSULTA DE AGENDA"
echo "=========================================="

if command -v ab &> /dev/null; then
  ab -n 500 -c 50 -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE_URL/citas/agenda?fecha=2026-10-06"
else
  seq 100 | xargs -n 1 -P 10 -I {} curl -s -o /dev/null -w "%{http_code}\n" \
    -X GET "$BASE_URL/citas/agenda?fecha=2026-10-06" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | sort | uniq -c
fi

echo -e "\n"
echo " --> PRUEBAS COMPLETADAS"
echo " "