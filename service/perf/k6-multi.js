import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: __ENV.VUS ? parseInt(__ENV.VUS, 10) : 100,
  duration: __ENV.DURATION || '60s',
};

const baseUrl = __ENV.API_BASE_URL || 'http://localhost:8080';

export default function () {
  const payload = JSON.stringify({
    amount: 45.67,
    currency: 'USD',
    payerId: `payer-${__VU}`,
    payeeId: `payee-${__ITER}`,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': `k6-multi-${__VU}-${__ITER}`,
    },
  };

  const res = http.post(`${baseUrl}/payments`, payload, params);
  check(res, {
    'status is 202 or 200': (r) => r.status === 202 || r.status === 200,
  });
  sleep(0.05);
}
