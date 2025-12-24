import http from "lyocell/http";
import { check } from "lyocell";

export default function() {
    // Send a JSON encoded POST request
    let body = JSON.stringify({ key: "value" });
    let res = http.post(`${__ENV.BASE_URL}/post`, body, { headers: { "Content-Type": "application/json" }});

    // Use JSON.parse to deserialize the JSON (instead of using the r.json() method)
    let j = JSON.parse(res.body);

    // Verify response
    check(res, {
        "status is 200": (r) => r.status === 200,
        "is key correct": (r) => j.json.key === "value",
    });
}
