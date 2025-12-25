# Utility Modules

## Execution (`lyocell/execution`)
```javascript
import execution from 'lyocell/execution';

export default function () {
  const id = execution.vu.idInTest;           // 1..N
  const iter = execution.vu.iterationInInstance;
  // execution.test.abort(); // stop the entire test
}
```

## Data (`lyocell/data`)
```javascript
import { SharedArray } from 'lyocell/data';

const users = new SharedArray('users', () => {
  return JSON.parse(open('./users.json'));
});

export default function () {
  const user = users[Math.floor(Math.random() * users.length)];
  // use user
}
```

## Crypto (`lyocell/crypto`)
```javascript
import crypto from 'lyocell/crypto';

const sig = crypto.hmac('sha256', 'secret', 'message', 'hex');
const hash = crypto.sha256('payload');
```

## Encoding (`lyocell/encoding`)
```javascript
import encoding from 'lyocell/encoding';

const b64 = encoding.b64encode('hello');
const plain = encoding.b64decode(b64);
```

