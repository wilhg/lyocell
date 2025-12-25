# Outputs & Reports

Configure outputs via CLI `-o` or `options.lyocell.outputs`.

## CLI
```bash
lyocell test.js -o html=report.html
```

## JS Options
```javascript
export const options = {
  lyocell: {
    outputs: [
      { type: 'html', target: 'report.html' },
    ],
  },
};
```

## What you get
- **Console summary**: live, k6-style table at the end of the run.
- **HTML report**: standalone, offline page with charts and metrics (iterations timeline, checks, latency percentiles).

## Tips
- Use unique report names per run if you want to keep history (e.g., `report-${Date.now()}.html`).
- Threshold failures raise errors after the run; set `thresholds` in `options` to enforce SLOs.

