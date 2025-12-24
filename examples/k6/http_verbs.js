import http from "lyocell/http";
import { check, group } from "lyocell";

export default function() {
    // GET request
    group("GET", function() {
        let res = http.get(`${__ENV.BASE_URL}/get?verb=get`);
        check(res, {
            "status is 200": (r) => r.status === 200,
            "is verb correct": (r) => r.json().args.verb === "get",
        });
    });

    // POST request
    group("POST", function() {
        let res = http.post(`${__ENV.BASE_URL}/post`, { verb: "post" });
        check(res, {
            "status is 200": (r) => r.status === 200,
            "is verb correct": (r) => r.json().form.verb === "post",
        });
    });

    // PUT request
    group("PUT", function() {
        let res = http.put(`${__ENV.BASE_URL}/put`, JSON.stringify({ verb: "put" }), { headers: { "Content-Type": "application/json" }});
        check(res, {
            "status is 200": (r) => r.status === 200,
            "is verb correct": (r) => r.json().json.verb === "put",
        });
    });

    // PATCH request
    group("PATCH", function() {
        let res = http.patch(`${__ENV.BASE_URL}/patch`, JSON.stringify({ verb: "patch" }), { headers: { "Content-Type": "application/json" }});
        check(res, {
            "status is 200": (r) => r.status === 200,
            "is verb correct": (r) => r.json().json.verb === "patch",
        });
    });

    // DELETE request
    group("DELETE", function() {
        let res = http.del(`${__ENV.BASE_URL}/delete?verb=delete`);
        check(res, {
            "status is 200": (r) => r.status === 200,
            "is verb correct": (r) => r.json().args.verb === "delete",
        });
    });
}
