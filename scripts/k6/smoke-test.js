import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,
  duration: '1m',
};

export default function () {
  const payload = JSON.stringify({
    order_id: `ord_${__VU}_${__ITER}`,
    amount: 100,
    currency: 'USD',
    items: ['item1', 'item2'],
    user: { name: 'Test', email: 'test@test.com' }
  });

  const res = http.post('http://localhost:8090/api/order', payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(0.01);
}
