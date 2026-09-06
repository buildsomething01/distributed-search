import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    search: {
      executor: 'constant-arrival-rate',
      rate: 1000,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 150,
      maxVUs: 600,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const url = __ENV.SEARCH_URL || 'http://localhost:8080/v1/search';

export default function () {
  const payload = JSON.stringify({ query: 'remote work policy', limit: 10 });
  const headers = {
    'Content-Type': 'application/json',
    'X-Tenant-Id': __ENV.TENANT_ID || 'acme',
  };
  if (__ENV.ACCESS_TOKEN) {
    headers.Authorization = `Bearer ${__ENV.ACCESS_TOKEN}`;
  }

  const response = http.post(url, payload, { headers });
  check(response, { 'search returned 200': (r) => r.status === 200 });
}
