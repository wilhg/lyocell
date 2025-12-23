import http from 'k6/http';
import { check } from 'k6';

export default function() {
    const baseUrl = __ENV.BASE_URL || 'http://localhost:80';
    const url = baseUrl + '/post';
    const payload = JSON.stringify({
        name: 'Lyocell User',
        role: 'Load Tester',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const res = http.post(url, payload, params);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'response contains name': (r) => r.json().json.name === 'Lyocell User',
    });
}
