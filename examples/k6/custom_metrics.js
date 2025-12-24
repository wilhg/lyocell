import http from "lyocell/http";
import { Counter, Gauge, Rate, Trend } from "lyocell/metrics";
import { check } from "lyocell";

let myCounter = new Counter("my_counter");
let myGauge = new Gauge("my_gauge");
let myRate = new Rate("my_rate");
let myTrend = new Trend("my_trend");

let maxResponseTime = 0.0;

export default function () {
    let res = http.get(`${__ENV.BASE_URL}/`);
    let passed = check(res, { "status is 200": (r) => r.status === 200 });

    // Add one for number of requests
    myCounter.add(1);

    // Set max response time seen
    maxResponseTime = Math.max(maxResponseTime, res.timings.duration);
    myGauge.add(maxResponseTime);

    // Add check success or failure to keep track of rate
    myRate.add(passed);

    // Keep track of TCP-connecting and TLS handshaking part of the response time
    myTrend.add(res.timings.connecting + res.timings.tls_handshaking);
}