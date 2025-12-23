import { Counter, Trend } from 'k6/metrics';
import { sleep } from 'k6';

const myCounter = new Counter('my_counter');
const myTrend = new Trend('response_time_trend');

export const options = {
    thresholds: {
        'my_counter': ['count>10'], // Total count must be > 10
    },
};

export default function() {
    myCounter.add(1);
    
    // Simulate a random latency between 10ms and 100ms
    const latency = Math.random() * 90 + 10;
    myTrend.add(latency);
    
    sleep(0.1);
}
