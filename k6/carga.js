// ============================================================
// Pruebas de carga de la API de la clínica veterinaria (k6).
//
// Uso:
//   k6 run k6/carga.js
//   k6 run --env BASE_URL=http://localhost:8090 -e VUS=10 -e DURACION=30s k6/carga.js
//
// Métricas propias:
//   login_fallidos        — logins con status != 200
//   operaciones_fallidas  — llamadas de negocio con status inesperado
// ============================================================
import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// El flujo incluye a propósito una llamada sin token que responde 401.
// Se marca como esperada para que no cuente como fallo en http_req_failed.
http.setResponseCallback(http.expectedStatuses(200, 201, 401));

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';
const DURACION = __ENV.DURACION || '1m';
const VUS = Number(__ENV.VUS || 5);

const loginFallidos = new Counter('login_fallidos');
const operacionesFallidas = new Counter('operaciones_fallidas');
const duracionLogin = new Trend('duracion_login', true);

export const options = {
  scenarios: {
    flujo_diario: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURACION,
      gracefulStop: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    login_fallidos: ['count==0'],
    operaciones_fallidas: ['count==0'],
  },
};

const CREDENCIALES = JSON.stringify({
  email: 'cliente@veterinaria.com',
  password: 'Cliente123!',
});

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function autenticar() {
  const inicio = Date.now();
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, CREDENCIALES, {
    headers: JSON_HEADERS,
    tags: { name: 'login' },
  });
  duracionLogin.add(Date.now() - inicio);

  const ok = check(res, {
    'login status 200': (r) => r.status === 200,
    'login devuelve token': (r) => {
      try {
        return Boolean(JSON.parse(r.body).token);
      } catch (e) {
        return false;
      }
    },
  });
  if (!ok) {
    loginFallidos.add(1);
    return null;
  }
  return JSON.parse(res.body).token;
}

function autorizado(token) {
  return { ...JSON_HEADERS, Authorization: `Bearer ${token}` };
}

export default function () {
  let token = null;

  group('autenticación', () => {
    token = autenticar();

    const sinToken = http.get(`${BASE_URL}/api/v1/mascotas/mis-mascotas`, {
      headers: JSON_HEADERS,
      tags: { name: 'sin_token' },
    });
    check(sinToken, { 'sin token → 401': (r) => r.status === 401 });
  });

  if (!token) {
    return;
  }

  group('consulta', () => {
    const me = http.get(`${BASE_URL}/api/v1/auth/me`, {
      headers: autorizado(token),
      tags: { name: 'me' },
    });
    if (!check(me, { '/auth/me → 200': (r) => r.status === 200 })) {
      operacionesFallidas.add(1);
    }

    const mascotas = http.get(`${BASE_URL}/api/v1/mascotas/mis-mascotas`, {
      headers: autorizado(token),
      tags: { name: 'mis_mascotas' },
    });
    if (!check(mascotas, { 'mis-mascotas → 200': (r) => r.status === 200 })) {
      operacionesFallidas.add(1);
    }
  });

  sleep(1);
}
