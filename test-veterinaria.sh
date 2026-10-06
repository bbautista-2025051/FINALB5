#!/usr/bin/env bash
# ============================================================
# Prueba funcional de extremo a extremo (E2E) de la API de la
# clínica veterinaria a través del api-gateway.
#
# Uso:  ./test-veterinaria.sh
#       BASE_URL=http://localhost:8090 ./test-veterinaria.sh
#
# Requisitos: curl, date (GNU o BSD). No requiere jq.
# Sale con código != 0 si alguna verificación falla.
# ============================================================
set -u

BASE_URL="${BASE_URL:-http://localhost:8088}"
TMP_BODY="$(mktemp)"
trap 'rm -f "$TMP_BODY"' EXIT

PASS=0
FAIL=0

if [ -t 1 ]; then
  VERDE=$'\033[32m'; ROJO=$'\033[31m'; AMARILLO=$'\033[33m'; SIN_COLOR=$'\033[0m'
else
  VERDE=""; ROJO=""; AMARILLO=""; SIN_COLOR=""
fi

CODIGO=""
CUERPO=""

peticion() { # metodo url [token] [json_body]
  local metodo="$1" url="$2" token="${3:-}" cuerpo="${4:-}"
  local args=(-sS -o "$TMP_BODY" -w "%{http_code}" -X "$metodo" "$BASE_URL$url"
              -H "Content-Type: application/json" --connect-timeout 5 --max-time 30)
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$cuerpo" ] && args+=(-d "$cuerpo")
  CODIGO=$(curl "${args[@]}" 2>"$TMP_BODY.err")
  if [ -z "$CODIGO" ]; then CODIGO="000"; fi
  CUERPO=$(cat "$TMP_BODY" 2>/dev/null || echo "")
}

esperado() { # codigo_esperado descripcion
  if [ "$CODIGO" = "$1" ]; then
    PASS=$((PASS + 1))
    echo "${VERDE}[OK]${SIN_COLOR} $2 ($CODIGO)"
  else
    FAIL=$((FAIL + 1))
    echo "${ROJO}[FALLO]${SIN_COLOR} $2 — esperado $1, obtenido $CODIGO"
    echo "        cuerpo: $(echo "$CUERPO" | head -c 400)"
  fi
}

extraer() { # campo_del_json (primer nivel)
  echo "$CUERPO" | sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" | head -n 1
}

fecha_futura() { # hora_local "10:00" -> yyyy-MM-ddTHH:mm:ss (mañana)
  local hora="$1" salida
  salida=$(date -u -d "+1 day $hora" +%Y-%m-%dT%H:%M:%S 2>/dev/null)
  if [ -z "$salida" ]; then
    local manana
    manana=$(date -u -v+1d +%Y-%m-%d 2>/dev/null)
    salida=$(date -u -v+1d -j -f "%Y-%m-%d %H:%M" "$manana $hora" +%Y-%m-%dT%H:%M:%S 2>/dev/null)
  fi
  if [ -z "$salida" ]; then
    echo "${AMARILLO}No se pudo calcular la fecha con date(1)${SIN_COLOR}" >&2
    return 1
  fi
  echo "$salida"
}

echo "=============================================="
echo " Prueba E2E — clínica veterinaria"
echo " Gateway: $BASE_URL"
echo "=============================================="

# 0) Disponibilidad del gateway
peticion GET /actuator/health
esperado 200 "Gateway y servicios arriba"

# 1) Autenticación
peticion POST /api/v1/auth/login '{"email":"admin@veterinaria.com","password":"Admin123!"}'
esperado 200 "Login admin"
TOKEN_ADMIN=$(extraer token)

peticion GET /api/v1/auth/me "$TOKEN_ADMIN"
esperado 200 "GET /auth/me con token de admin"

peticion POST /api/v1/auth/login '{"email":"admin@veterinaria.com","password":"clave-mala"}'
esperado 401 "Login con contraseña incorrecta → 401"

peticion GET /api/v1/citas
esperado 401 "Petición sin token → 401"

EMAIL_E2E="e2e-$(date +%s)@test.com"
peticion POST /api/v1/auth/registro \
  "{\"nombre\":\"Cliente E2E\",\"telefono\":\"3009998888\",\"email\":\"$EMAIL_E2E\",\"password\":\"Cliente123!\",\"rol\":\"ADMIN\"}"
esperado 201 "Registro público (rol ADMIN del body se ignora → CLIENTE)"
TOKEN_NUEVO=$(extraer token)

peticion GET /api/v1/auth/me "$TOKEN_NUEVO"
esperado 200 "GET /auth/me con el usuario recién registrado"
ROL_NUEVO=$(extraer rol)
if [ "$ROL_NUEVO" = "CLIENTE" ]; then
  PASS=$((PASS + 1)); echo "${VERDE}[OK]${SIN_COLOR} El rol del body fue ignorado (rol=$ROL_NUEVO)"
else
  FAIL=$((FAIL + 1)); echo "${ROJO}[FALLO]${SIN_COLOR} Se esperaba rol CLIENTE y vino: $ROL_NUEVO"
fi

# 2) Tokens de los usuarios semilla
peticion POST /api/v1/auth/login '{"email":"veterinario@veterinaria.com","password":"Vet12345!"}'
esperado 200 "Login veterinario"
TOKEN_VET=$(extraer token)

peticion POST /api/v1/auth/login '{"email":"cliente@veterinaria.com","password":"Cliente123!"}'
esperado 200 "Login cliente"
TOKEN_CLIENTE=$(extraer token)

peticion GET /api/v1/auth/me "$TOKEN_CLIENTE"
CLIENTE_ID=$(extraer id)

# 3) Mascotas
peticion POST /api/v1/mascotas '{"nombre":"Firulais E2E","especie":"PERRO","raza":"Mestizo","edad":3}' "$TOKEN_CLIENTE"
esperado 201 "Cliente crea su mascota"
MASCOTA_ID=$(extraer id)

peticion GET "/api/v1/mascotas/mis-mascotas" "$TOKEN_CLIENTE"
esperado 200 "Cliente lista sus mascotas"

peticion GET "/api/v1/mascotas/$MASCOTA_ID" "$TOKEN_CLIENTE"
esperado 403 "Cliente no puede usar GET /mascotas/{id} (solo VET/ADMIN) → 403"

peticion GET "/api/v1/mascotas/$MASCOTA_ID" "$TOKEN_VET"
esperado 200 "Veterinario consulta la mascota por id"

# 4) Citas y reglas de negocio
FECHA_A=$(fecha_futura "10:00")
FECHA_B=$(fecha_futura "16:00")
FECHA_C=$(fecha_futura "17:00")

peticion GET /api/v1/auth/me "$TOKEN_VET"
esperado 200 "GET /auth/me del veterinario"
VET_ID=$(extraer id)

CUERPO_CITA="{\"mascotaId\":$MASCOTA_ID,\"veterinarioId\":$VET_ID,\"fechaHora\":\"$FECHA_A\",\"motivo\":\"Control general E2E\"}"
peticion POST /api/v1/citas "$CUERPO_CITA" "$TOKEN_CLIENTE"
esperado 201 "Cliente agenda cita con su veterinario"
CITA_A=$(extraer id)

peticion POST /api/v1/citas "$CUERPO_CITA" "$TOKEN_CLIENTE"
esperado 409 "Solape de horario (mismo veterinario, ±30 min) → 409"

CUERPO_CITA_B="{\"mascotaId\":$MASCOTA_ID,\"veterinarioId\":$VET_ID,\"fechaHora\":\"$FECHA_B\",\"motivo\":\"Segunda consulta E2E\"}"
peticion POST /api/v1/citas "$CUERPO_CITA_B" "$TOKEN_CLIENTE"
esperado 201 "Segunda cita del día"
CITA_B=$(extraer id)

CUERPO_CITA_C="{\"mascotaId\":$MASCOTA_ID,\"veterinarioId\":$VET_ID,\"fechaHora\":\"$FECHA_C\",\"motivo\":\"Tercera consulta\"}"
peticion POST /api/v1/citas "$CUERPO_CITA_C" "$TOKEN_CLIENTE"
esperado 409 "Máximo 2 citas pendientes por cliente y día → 409"

FECHA_DIA="${FECHA_A%%T*}"
peticion GET "/api/v1/citas/agenda?fecha=$FECHA_DIA" "$TOKEN_VET"
esperado 200 "Veterinario consulta su agenda del día"

peticion GET "/api/v1/citas/$CITA_A" "$TOKEN_VET"
esperado 200 "Veterinario consulta la cita"

# 5) Expediente clínico (saga: guarda local y completa la cita vía servicio interno)
CUERPO_EXP="{\"citaId\":$CITA_A,\"diagnostico\":\"Animal sano, sin observaciones relevantes\",\"tratamiento\":\"Vacunación al día, dieta balanceada\",\"pesoKg\":18.5}"
peticion POST /api/v1/expedientes "$CUERPO_EXP" "$TOKEN_VET"
esperado 201 "Veterinario registra el expediente de su cita"

peticion POST /api/v1/expedientes "$CUERPO_EXP" "$TOKEN_VET"
esperado 409 "Una cita no puede tener dos expedientes → 409"

peticion GET "/api/v1/expedientes/mascotas/$MASCOTA_ID" "$TOKEN_CLIENTE"
esperado 200 "Historial de la mascota visible para su dueño"
TOTAL=$(extraer totalElements)
if [ "$TOTAL" = "1" ] || [ "$TOTAL" = "2" ]; then
  PASS=$((PASS + 1)); echo "${VERDE}[OK]${SIN_COLOR} Historial con $TOTAL registro(s)"
else
  FAIL=$((FAIL + 1)); echo "${ROJO}[FALLO]${SIN_COLOR} Historial inesperado: totalElements=$TOTAL"
fi

# 6) Cancelación con antelación (>2 h)
peticion PATCH "/api/v1/citas/$CITA_B/cancelar" "" "$TOKEN_CLIENTE"
esperado 200 "Cliente cancela su segunda cita con antelación"

# 7) Autorización cruzada
peticion GET "/api/v1/usuarios" "$TOKEN_CLIENTE"
esperado 403 "Cliente sin acceso al CRUD de usuarios → 403"

peticion GET "/api/v1/usuarios/veterinarios" "$TOKEN_CLIENTE"
esperado 200 "Cualquier autenticado puede listar veterinarios"

peticion GET "/api/v1/usuarios/veterinarios" "$TOKEN_VET"
esperado 200 "El veterinario puede listar veterinarios"

echo "=============================================="
if [ "$FAIL" -eq 0 ]; then
  echo "${VERDE}RESULTADO: $PASS/$PASS verificaciones correctas${SIN_COLOR}"
  exit 0
else
  echo "${ROJO}RESULTADO: $FAIL fallo(s), $PASS correctas${SIN_COLOR}"
  exit 1
fi
