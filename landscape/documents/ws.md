# WebSocket Module (`lyocell/ws`)

The `lyocell/ws` module provides a WebSocket client that mirrors the k6 API.

## Methods

### `ws.connect(url, [params], callback)`
Connects to a WebSocket server.

*   `url` (string): The WebSocket URL (e.g., `ws://...` or `wss://...`).
*   `params` (object): Optional configuration.
    *   `headers`: Custom headers to send during the handshake.
    *   `tags`: Custom metric tags for this connection.
*   `callback` (function): A function that receives the `socket` object.

## Socket Object
The socket object passed to the callback supports the following methods:

*   `socket.on(event, callback)`: Listen for events (`'open'`, `'message'`, `'close'`, `'error'`).
*   `socket.send(data)`: Send a text message.
*   `socket.close([code])`: Close the connection.
*   `socket.setTimeout(fn, delay)`: VU-safe timeout within the socket context.
*   `socket.setInterval(fn, delay)`: VU-safe interval within the socket context.

## Example
```javascript
import ws from 'lyocell/ws';
import { check } from 'lyocell';

export default function () {
  ws.connect('wss://echo.websocket.org', {}, function (socket) {
    socket.on('open', () => {
      socket.send('hello');
    });

    socket.on('message', (data) => {
      check(data, { 'message is hello': (d) => d === 'hello' });
      socket.close();
    });
  });
}
```

