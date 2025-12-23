import http from 'lyocell/http';
import { check, sleep } from 'lyocell';

export const options = {
    thresholds: {
        'checks': ['rate>0.95'], // 95% of requests must pass the check
    },
};

export default function() {
    // Make sure you have httpbun running!
    // cd examples && docker-compose up
    const baseUrl = __ENV.BASE_URL || 'http://localhost:80';
    const res = http.get(baseUrl + '/get');

    check(res, {
        'status is 200': (r) => r.status === 200,
        'content type is json': (r) => r.headers['content-type'].includes('application/json'),
    });
}
