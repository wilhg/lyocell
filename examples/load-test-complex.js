import http from 'lyocell/http';
import { check, sleep } from 'lyocell';
import crypto from 'lyocell/crypto';
import execution from 'lyocell/execution';

/*
 * STAGED LOAD TEST CONFIGURATION
 * Total: 100,000 requests
 * 
 * Stage 1: 20,000 requests (20%) in 5s
 * Wait: 2s
 * Stage 2: 30,000 requests (30%) after 7s (5s + 2s)
 * Wait: 3s
 * Stage 3: 50,000 requests (50%) after 10s (7s + 3s)
 */
export const options = {
    scenarios: {
        stage_1: {
            executor: 'shared-iterations',
            vus: 50,
            iterations: 200,
            startTime: '0s',
        },
        stage_2: {
            executor: 'shared-iterations',
            vus: 50,
            iterations: 1000,
            startTime: '1s', // 5s duration + 2s wait
        },
        stage_3: {
            executor: 'shared-iterations',
            vus: 5,
            iterations: 50,
            startTime: '3s', // 7s + 3s wait (assuming stage 2 finishes or just starting then)
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<500'],
    },
};

export default function () {
    const vuId = execution.vu.idInTest;
    const iter = execution.vu.iterationInInstance;
    const baseUrl = __ENV.BASE_URL || 'http://localhost:80';

    const payload = JSON.stringify({
        header: {
            vu: vuId,
            iteration: iter,
            timestamp: new Date().toISOString(),
            signature: crypto.sha256(`session-${vuId}-${iter}`, 'hex')
        },
        body: {
            message: "Complex staged load test"
        }
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-VU-ID': vuId.toString()
        },
        timeout: '1s'
    };

    const res = http.post(`${baseUrl}/post`, payload, params);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'data integrity': (r) => r.json().json.header.vu === vuId
    });
}