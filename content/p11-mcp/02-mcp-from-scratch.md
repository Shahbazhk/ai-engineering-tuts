---
title: An MCP Server and Client from Scratch
summary: JSON-RPC 2.0 over stdio — lifecycle, tools/list, tools/call, error semantics — in two small Java classes with an end-to-end test.
minutes: 55
prereqs: [p11-mcp/01-mcp-concepts]
tags: [mcp, json-rpc, from-scratch]
quiz:
  - q: How does an MCP server respond to a JSON-RPC notification (a message without an id)?
    options: [With an empty result, It does not respond at all, With an error, With an acknowledgement]
    answer: 1
    why: Notifications never get responses — the stdio test asserts two responses for two requests plus one notification.
  - q: A tool fails because the user lacks permission. How should the server report it?
    options:
      - A JSON-RPC error with code -32603
      - A normal result with isError true and an explanatory text, so the model can see and react to it
      - Close the connection
      - Return an empty result
    answer: 1
    why: Protocol errors are for protocol problems (unknown method, bad params); tool execution failures are results.
  - q: On the stdio transport, where must a server write its log messages?
    options: [stdout, stderr (stdout carries protocol messages only), A random file, Nowhere]
    answer: 1
    why: Anything non-JSON on stdout corrupts the protocol stream.
interview:
  - q: What does capability and version negotiation in MCP give you?
    a: |
      Forward and backward compatibility between independently released hosts and servers. In initialize the client sends its protocol version and capabilities (e.g. sampling, roots, elicitation); the server answers with the version it will speak (the client's if supported, otherwise its own latest) and its capabilities (tools, resources, prompts, logging, listChanged notifications). Both sides then only use features the other declared. This lets an enterprise roll out servers and hosts on different schedules and degrade gracefully, much like HTTP content negotiation or TLS version negotiation.
    level: senior
---

## Why it matters

Frameworks make MCP a few annotations, but when a connection fails in production you will be reading raw JSON-RPC. Building the protocol once makes those logs readable — and shows how little magic there is.

## Concept

```mermaid
sequenceDiagram
    participant C as Client (host)
    participant S as Server
    C->>S: request id 1 initialize (protocolVersion 2025-06-18, capabilities)
    S-->>C: result id 1 (protocolVersion, capabilities.tools, serverInfo)
    C->>S: notification notifications/initialized (no id, no reply)
    C->>S: request id 2 tools/list
    S-->>C: result id 2 (tools with name, description, inputSchema)
    C->>S: request id 3 tools/call (name search_content, arguments)
    S-->>C: result id 3 (content text, isError false)
```

Error semantics:

| Situation | Response |
|---|---|
| Invalid JSON | JSON-RPC error `-32700` (parse error) |
| Unknown method | `-32601` (method not found) |
| Unknown tool / bad params | `-32602` (invalid params) |
| Tool ran and failed (e.g. permission) | **Result** with `isError: true` |

## Build it from scratch

The dispatcher — notifications get no reply; tools require completed initialisation:

<!-- code: lessons/p11-mcp/src/main/java/dev/aieng/p11/McpServer.java#dispatch -->

Tools listing and invocation, including the result-vs-error distinction:

<!-- code: lessons/p11-mcp/src/main/java/dev/aieng/p11/McpServer.java#tools -->

The stdio transport — one JSON message per line; logs to stderr:

<!-- code: lessons/p11-mcp/src/main/java/dev/aieng/p11/McpServer.java#stdio -->

And the client side a host runs:

<!-- code: lessons/p11-mcp/src/main/java/dev/aieng/p11/McpClient.java#lifecycle -->

The tests connect client and server in memory and cover the full lifecycle, tool discovery with JSON schemas, `isError` results for permission failures and missing arguments, protocol errors for unknown tools/methods and bad JSON, version negotiation, and the stdio transport with a mix of requests and notifications.

## Now with the library

The official **MCP Java SDK** (`io.modelcontextprotocol.sdk:mcp`) implements this — plus resources, prompts, sampling, streaming HTTP and session management — and Spring AI builds its MCP starters on it (next lesson). Run the demo server from this lesson with any MCP host that supports stdio servers:

```json
{ "mcpServers": { "course-demo": {
    "command": "java",
    "args": ["-cp", "lessons/p11-mcp/target/classes:<jackson jars>", "dev.aieng.p11.McpServer"] } } }
```

## Production notes

- **Never print to stdout** in a stdio server except protocol messages.
- **Validate tool arguments** against the schema you published — models make mistakes.
- **Keep responses small:** tool results enter the model's context window.

## Exercises

1. **Easy** — Add `resources/list` returning one static resource and a test for it.
2. **Medium** — Add `notifications/tools/list_changed` support: when a tool is registered at run time, the server sends that notification and the client re-lists.
3. **Hard** — Implement a minimal Streamable HTTP transport for `McpServer` with the JDK `HttpServer`: POST a JSON-RPC message to `/mcp`, return the JSON response, and require an `Mcp-Session-Id` header after initialisation.

## Common mistakes

- Answering notifications.
- Using JSON-RPC errors for tool-level failures.
- Mixing logs into stdout.

## Further reading

- JSON-RPC 2.0 specification: <https://www.jsonrpc.org/specification>
- MCP specification, "Lifecycle", "Transports" and "Tools" sections.
