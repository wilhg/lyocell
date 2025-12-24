import http from "lyocell/http";
import { check } from "lyocell";

export let options = {
    thresholds: {
        // Declare a threshold over all HTTP response times,
        // the 95th percentile should not cross 500ms
        http_req_duration: ["p(95)<500"],

        // Declare a threshold over HTTP response times for all data points
        // where the URL tag is equal to `${__ENV.BASE_URL}/post`,
        // the max should not cross 1000ms
        "http_req_duration{name:${__ENV.BASE_URL}/post}": ["max<1000"],
    }
};

export default function() {
    http.get(`${__ENV.BASE_URL}/`);
    http.post(`${__ENV.BASE_URL}/post`, {data: "some data"});
}
