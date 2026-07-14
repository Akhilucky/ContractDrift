import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const driftTrend = new Trend('drift_score');
const gateTrend = new Trend('gate_latency');

export const options = {
  stages: [
    { duration: '2m', target: 100 },
    { duration: '5m', target: 200 },
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const SERVICES = [
  'order-service', 'payment-service', 'inventory-service',
  'shipping-service', 'notification-service', 'user-service',
  'product-service', 'cart-service', 'checkout-service', 'billing-service',
];

const PAIRS = [];
for (let i = 0; i < SERVICES.length; i++) {
  for (let j = 0; j < SERVICES.length; j++) {
    if (i !== j && PAIRS.length < 50) {
      PAIRS.push({ provider: SERVICES[i], consumer: SERVICES[j] });
    }
  }
}

export default function () {
  group('send traffic', () => {
    for (const pair of PAIRS.slice(0, 5)) {
      const payload = JSON.stringify({
        order_id: `ord_${pair.provider}_${__ITER}`,
        amount: Math.random() * 1000,
        items: ['item1'],
        metadata: { source: pair.provider, dest: pair.consumer }
      });
      const res = http.post('http://localhost:8090/api/order', payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: { provider: pair.provider, consumer: pair.consumer }
      });
      check(res, { 'status 200': (r) => r.status === 200 });
    }
    sleep(0.1);
  });

  if (__ITER % 10 === 0) {
    group('check drift', () => {
      const start = Date.now();
      const res = http.get('http://localhost:8084/api/v1/gate/promote?service=order-service&version=test&env=staging');
      gateTrend.add(Date.now() - start);
      driftTrend.add(res.json('drift_score'));
    });
  }
}
