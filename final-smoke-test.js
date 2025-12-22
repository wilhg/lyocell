import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

export const options = {
    thresholds: {
        'checks': ['rate>0.9'], // Fail if < 90% pass
    },
};

const myTrend = new Trend('custom_duration');
const myCounter = new Counter('custom_hits');

export function setup() {
    return { name: 'lyocell-user' };
}

export default function(data) {
    const res = http.get('https://httpbin.org/get');
    
    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    myTrend.add(Math.random() * 100);
    myCounter.add(1);
    
    sleep(0.1);
}
