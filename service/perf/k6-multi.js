import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween, randomItem } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
  vus: __ENV.VUS ? parseInt(__ENV.VUS, 10) : 100,
  duration: __ENV.DURATION || '60s',
};

const baseUrl = __ENV.API_BASE_URL || 'http://localhost:80';

function randomId(prefix) {
  return `${prefix}-${randomIntBetween(100000, 999999)}`;
}

function randomKey() {
  return `k6-${randomIntBetween(1, 1e9)}-${Date.now()}`;
}

export default function () {
  const payload = JSON.stringify({
    amount: 45.67,
    currency: 'USD',
    payerId: randomId('payer'),
    payeeId: randomId('payee'),
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': randomKey(),
    },
  };

  const res = http.post(`${baseUrl}/payments`, payload, params);
  check(res, {
    'status is 202 or 200': (r) => r.status === 202 || r.status === 200,
  });
  sleep(0.1);
}
