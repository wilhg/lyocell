# Experimental Modules

## File System (`lyocell/experimental/fs`)
Basic file system operations. Note: requires appropriate IO permissions.

*   `fs.open(path)`: Opens a file.
*   `fs.stat(path)`: Returns file metadata (size, etc.).
*   `fs.readFile(path)`: Reads the entire content of a file.

## CSV (`lyocell/experimental/csv`)
Utilities for parsing CSV data.

*   `csv.parse(data, [options])`: Parses a CSV string into an array of objects.
    *   `options.delimiter`: Custom delimiter (default: `,`).

## Secrets (`lyocell/secrets`)
Access secrets provided via environment or secure stores.

*   `secrets.get(name)`: Retrieves a secret value.

## Example
```javascript
import fs from 'lyocell/experimental/fs';
import csv from 'lyocell/experimental/csv';
import secrets from 'lyocell/secrets';

export default function () {
  const rawData = fs.readFile('./data.csv');
  const rows = csv.parse(rawData);
  const apiKey = secrets.get('API_KEY');
  
  console.log(`Parsed ${rows.length} rows using API Key`);
}
```
