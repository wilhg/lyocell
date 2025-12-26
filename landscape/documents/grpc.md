# gRPC Module (`lyocell/net/grpc`)

The `lyocell/net/grpc` module allows you to perform load testing against gRPC services.

## Client

### `new grpc.Client()`
Creates a new gRPC client instance.

### `client.connect(address, [options])`
Establishes a connection to a gRPC server.

*   `address` (string): The host and port (e.g., `localhost:50051`).
*   `options` (object):
    *   `plaintext` (boolean): Whether to use an unencrypted connection (default: `false`).
    *   `timeout` (string): Connection timeout (e.g., `"10s"`).

### `client.invoke(method, data, [params])`
Performs a unary gRPC call.

*   `method` (string): The full method name in `package.Service/Method` format.
*   `data` (object): The request message.
*   `params` (object): Optional call parameters (e.g., metadata headers).

## Example
```javascript
import grpc from 'lyocell/net/grpc';
import { check } from 'lyocell';

const client = new grpc.Client();

export default function () {
  client.connect('localhost:50051', { plaintext: true });

  const res = client.invoke('hello.HelloService/SayHello', { name: 'Lyocell' });

  check(res, {
    'status is OK': (r) => r.status === 0,
    'reply is correct': (r) => r.message.reply === 'Hello Lyocell'
  });

  client.close();
}
```

