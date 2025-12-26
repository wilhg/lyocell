# MCP Module (`lyocell/mcp`)

The `lyocell/mcp` module implements the **Model Context Protocol**, allowing you to load test MCP servers used by AI agents.

## Methods

### `mcp.connect(url, [options])`
Connects to an MCP server using the SSE (Server-Sent Events) transport.

## Client Methods

*   `client.initialize(params)`: Sends the standard MCP initialization request.
*   `client.listTools()`: Lists available tools on the server.
*   `client.callTool(name, arguments)`: Invokes a specific tool.
*   `client.listPrompts()`: Lists available prompts.
*   `client.getPrompt(name, arguments)`: Retrieves a specific prompt.
*   `client.listResources()`: Lists available resources.
*   `client.readResource(uri)`: Reads a specific resource.
*   `client.onNotification(method, callback)`: Listen for server notifications.
*   `client.onRequest(method, callback)`: Respond to server-initiated requests (e.g., sampling).
*   `client.close()`: Closes the connection.

## Example
```javascript
import mcp from 'lyocell/mcp';

export default function () {
  const client = mcp.connect('http://localhost:8080/sse');

  client.initialize({
    protocolVersion: '2024-11-05',
    clientInfo: { name: 'load-test', version: '1.0.0' }
  });

  const tools = client.listTools();
  const result = client.callTool('my-tool', { input: 'data' });

  client.close();
}
```

