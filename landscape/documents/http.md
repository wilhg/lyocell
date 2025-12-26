# HTTP Module (`lyocell/http`)

Import:
```javascript
import http from 'lyocell/http';
```

## Methods
| Method | Signature | Notes |
| --- | --- | --- |
| `get` | `http.get(url, params?)` | Simple GET. |
| `post` | `http.post(url, body, params?)` | Body is string/JSON. |
| `put` | `http.put(url, body, params?)` |  |
| `patch` | `http.patch(url, body, params?)` |  |
| `del` | `http.del(url, body?, params?)` |  |
| `batch` | `http.batch(requests)` | Run requests in parallel using virtual threads. |
| `cookieJar` | `http.cookieJar()` | Get the default VU-local cookie jar. |
| `CookieJar` | `new http.CookieJar()` | Create a new isolated cookie jar. |

## Params object
| Field | Type | Status |
| --- | --- | --- |
| `headers` | object | ✅ |
| `timeout` | string | ✅ (`"500ms"`, `"2s"`, `"1m"`) |
| `tags` | object | ✅ Metric tags for this request. |
| `cookies` | object | ✅ Map of cookies to send. |
| `auth` | string | ✅ `"basic"` or `"digest"` credentials. |
| `insecureSkipTLSVerify` | boolean | ✅ Skip server cert validation. |
| `maxRedirects` | number | ✅ Limit follow-redirects. |

## Response
| Field | Type | Notes |
| --- | --- | --- |
| `status` | number | HTTP status (0 on error). |
| `body` | string | Response body. |
| `headers` | object | Lower-cased keys. |
| `timings` | object | `{ duration, blocked, connecting, tls_handshaking, sending, waiting, receiving }`. |
| `proto` | string | HTTP version (e.g. `HTTP_1_1`). |
| `tls_info` | object | `{ version, cipher_suite }`. |
| `ocsp` | object | `{ status }`. |
| `json()` | function | Parse body as JSON. |
| `html(selector?)` | function | Parse body as HTML; returns queryable selection. |

## Example
```javascript
const res = http.post('https://httpbun.com/post', JSON.stringify({ hello: 'world' }), {
  headers: { 'Content-Type': 'application/json' },
  timeout: '2s',
});

console.log(res.status, res.timings.duration);
```

