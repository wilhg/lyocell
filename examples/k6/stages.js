import http from "lyocell/http";
import { check } from "lyocell";

export let options = {
    stages: [
        // Ramp-up from 1 to 5 VUs in 10s
        { duration: "10s", target: 5 },

        // Stay at rest on 5 VUs for 5s
        { duration: "5s", target: 5 },

        // Ramp-down from 5 to 0 VUs for 5s
        { duration: "5s", target: 0 }
    ]
};

export default function() {
    let res = http.get(`${__ENV.BASE_URL}/`);
    check(res, { "status is 200": (r) => r.status === 200 });
}