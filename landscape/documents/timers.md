# Timers Module (`lyocell/timers`)

The `lyocell/timers` module provides standard JavaScript timer functions that are compatible with the Lyocell virtual thread and event-loop architecture.

## Functions

### `setTimeout(callback, delay, ...args)`
Schedules the execution of a function after a delay.

### `setInterval(callback, delay, ...args)`
Repeatedly calls a function with a fixed time delay between each call.

### `clearTimeout(id)`
Cancels a timeout previously established by calling `setTimeout()`.

### `clearInterval(id)`
Cancels an interval previously established by calling `setInterval()`.

## Important Note
Unlike standard Node.js/Browser timers, these are managed by the Lyocell `JsEngine` event loop. They are safe to use within your `default` function and will be cleaned up automatically when the VU iteration finishes or the engine is closed.

## Example
```javascript
import { setTimeout, setInterval, clearInterval } from 'lyocell/timers';

export default function () {
  const timeoutId = setTimeout(() => {
    console.log('One second passed');
  }, 1000);

  const intervalId = setInterval(() => {
    console.log('Tick');
  }, 500);

  // cleanup after some time
  setTimeout(() => clearInterval(intervalId), 3000);
}
```

