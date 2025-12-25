# Scenarios

Use `options.scenarios` for advanced workload models. Executors:

| Executor | Purpose |
| --- | --- |
| `shared-iterations` | Fixed iterations shared across VUs. |
| `per-vu-iterations` | Fixed iterations per VU. |
| `constant-vus` | Fixed VUs for a duration. |
| `ramping-vus` | Stage-based up/down. |
| `constant-arrival-rate` | Target request rate (open model). |

## Ramping VUs
```javascript
export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 20 },
        { duration: '2m',  target: 20 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
};
```

## Constant Arrival Rate
```javascript
export const options = {
  scenarios: {
    api: {
      executor: 'constant-arrival-rate',
      rate: 200,        // iterations per timeUnit
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
};
```

## Per-VU Iterations
```javascript
export const options = {
  scenarios: {
    seeding: {
      executor: 'per-vu-iterations',
      vus: 10,
      iterations: 100, // per VU
    },
  },
};
```

## Shared Iterations
```javascript
export const options = {
  scenarios: {
    smoke: {
      executor: 'shared-iterations',
      vus: 5,
      iterations: 50, // total shared
      maxDuration: '1m',
    },
  },
};
```

