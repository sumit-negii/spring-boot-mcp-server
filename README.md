# MCP Task Orchestrator
### AI-powered MCP server using Spring Boot, OAuth 2.0, Streamable HTTP, and Claude integration

A Spring Boot implementation of a **Model Context Protocol (MCP) server** that enables Claude AI to orchestrate tasks, projects, and users through natural language — secured end-to-end with **OAuth 2.0 Authorization Code Flow + PKCE** and integrated with Claude.ai's MCP connector.

---

## Highlights

- **15 MCP tools** across Task, Project, and User domains — callable by Claude AI via natural language
- **OAuth 2.0 + PKCE** (RFC 7636) secured with Keycloak — no client secret, cryptographic challenge instead
- **Streamable HTTP transport** (MCP spec 2025-03-26) — single `POST /mcp` endpoint, replaces deprecated HTTP+SSE
- **Discovery endpoints** (RFC 8414 + RFC 9728) — MCP clients locate the authorization server automatically, zero hardcoded URLs
- **Spring AI MCP Server** — `@Tool`-annotated methods auto-registered via `ToolCallbackProvider`

> Full authentication setup, Keycloak configuration, tunnel setup, and Claude.ai integration: see [MCP OAuth 2.0 Authentication Guide](MCP_OAUTH_GUIDE.md)

---

## What is MCP?

**Model Context Protocol (MCP)** is an open standard by Anthropic that lets AI models like Claude communicate with external tools and data sources in a standardized way — a universal plugin system for AI.

```
User: "Show me all critical tasks assigned to Bob"
  ↓
Claude calls: list_tasks(priority="CRITICAL", assigneeId=2)
  ↓
MCP Server queries the database and returns results
  ↓
Claude formats and presents the answer
```

---

## MCP Transport

A **transport** defines how the MCP client (Claude) and MCP server communicate. This project uses **Streamable HTTP** — the current MCP standard (March 2025).

| Transport | Status | How it works |
|-----------|--------|--------------|
| STDIO | Active | Client spawns server as subprocess via stdin/stdout — desktop tools only |
| HTTP + SSE | Deprecated (March 2025) | Two endpoints + bridge tool (`mcp-remote`) |
| **Streamable HTTP** | **Current standard** | Single `POST /mcp` — plain JSON or SSE stream, no bridge needed |

---

## Architecture

```
┌─────────────────────────────┐
│         Claude.ai           │   MCP Client (OAuth Client)
└──────────────┬──────────────┘
               │ HTTPS via ngrok tunnel
               ▼
┌─────────────────────────────┐
│   Spring Boot MCP Server    │   Resource Server — validates JWT, exposes tools
│   POST /mcp                 │
│   /.well-known/oauth-*      │   OAuth discovery endpoints (RFC 8414, RFC 9728)
└──────────────┬──────────────┘
               │ JWT validation (JWKS)
               ▼
┌─────────────────────────────┐
│         Keycloak            │   Authorization Server — issues signed JWTs
│   (via Cloudflare tunnel)   │
└─────────────────────────────┘
```

The MCP server never handles login itself — it rejects unauthenticated requests with `401`, exposes discovery endpoints, and validates JWTs on every request.

---

## Project Structure

```
src/main/java/dev/mcp/server/
├── McpServerApplication.java
│
├── config/
│   ├── McpToolConfig.java          Registers all 15 tools with MCP framework
│   └── SecurityConfig.java         OAuth 2.0 resource server + discovery endpoints
│
├── domain/                         JPA entities
│   ├── enums/                      ProjectStatus, TaskPriority, TaskStatus, UserRole
│   ├── Project.java
│   ├── Task.java
│   └── User.java
│
├── repository/                     Spring Data JPA repositories
├── service/                        Business logic
│
├── tools/                          MCP tool methods (@Tool annotated)
│   ├── TaskTools.java              8 tools
│   ├── ProjectTools.java           5 tools
│   └── UserTools.java              2 tools
│
└── init/
    └── DataInitializer.java        Seeds sample data on startup
```

---

## MCP Tools (15 total)

### Task Tools (8)

| Tool | Description |
|------|-------------|
| `create_task` | Create a new task |
| `list_tasks` | List tasks — filter by status, priority, project, assignee |
| `get_task` | Get a task by ID |
| `update_task` | Update task fields (title, description, priority, dueDate) |
| `delete_task` | Delete a task |
| `assign_task` | Assign a task to a user |
| `update_task_status` | Change task status |
| `search_tasks` | Full-text search in title and description |

### Project Tools (5)

| Tool | Description |
|------|-------------|
| `create_project` | Create a new project |
| `list_projects` | List projects — filter by status |
| `get_project_with_tasks` | Get a project and all its tasks |
| `update_project` | Update project fields |
| `delete_project` | Delete a project |

### User Tools (2)

| Tool | Description |
|------|-------------|
| `list_users` | List users — filter by role |
| `get_user_tasks` | Get a user profile and their assigned tasks |

---

## Getting Started

### Prerequisites

- **Java 21+** — [Download here](https://adoptium.net)
- **Node.js 18+** — [Download here](https://nodejs.org) *(for MCP Inspector)*
- **Docker** — *(for Keycloak, if using OAuth)*

### Start the Server

```bash
./gradlew bootRun
```

Starts on **port 8085** and seeds sample data:

```
[DataInitializer] Sample data created: 4 users, 3 projects, 7 tasks
Tomcat started on port(s): 8085
```

Sample data: Alice (MANAGER), Bob / Carol (DEVELOPER), Dave (ADMIN) — 3 projects, 7 tasks.

---

## Quick Test — MCP Inspector (No Auth Required)

The fastest way to verify the server and call tools locally:

```bash
npx @modelcontextprotocol/inspector
```

Opens at `http://localhost:6274`. Connect with:

| Field | Value |
|-------|-------|
| Transport | Streamable HTTP |
| URL | `http://localhost:8085/mcp` |

All 15 tools appear immediately. Call any tool and see the raw JSON response.

> For OAuth-secured testing and Claude.ai integration, see [MCP OAuth 2.0 Authentication Guide](MCP_OAUTH_GUIDE.md).

---

## H2 Console

Inspect the in-memory database while the server is running:

```
http://localhost:8085/h2-console
```

| Field | Value |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:taskdb` |
| Username | `sa` |
| Password | *(leave empty)* |

---

## How Tools Are Registered

Spring AI scans for `ToolCallbackProvider` beans and exposes them via the MCP transport automatically.

```java
// McpToolConfig.java
@Bean
public ToolCallbackProvider taskManagementToolCallbackProvider(
        TaskTools taskTools, ProjectTools projectTools, UserTools userTools) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(taskTools, projectTools, userTools)
            .build();
}
```

```java
// Example tool — Spring AI generates JSON Schema from these annotations
@Tool(name = "list_tasks", description = "List tasks with optional filters.")
public List<Task> listTasks(
        @ToolParam(description = "Filter by status: TODO, IN_PROGRESS, REVIEW, DONE, CANCELLED", required = false) String status,
        @ToolParam(description = "Filter by priority: LOW, MEDIUM, HIGH, CRITICAL", required = false) String priority) {
    // ...
}
```

---

## Tech Stack

| Technology | Version | Role |
|------------|---------|------|
| Spring Boot | 3.4.3 | Application framework |
| Spring AI MCP Server | 1.1.3 | MCP protocol + Streamable HTTP transport |
| Spring Security OAuth2 | via Boot | JWT validation (resource server) |
| Keycloak | latest | Authorization server (OAuth 2.0 + PKCE) |
| Spring Data JPA | via Boot | Database abstraction |
| H2 Database | via Boot | In-memory database |
| Lombok | via Boot | Boilerplate reduction |
| Java | 21 | Language |
| Gradle | 8.13 | Build tool |

---

## Resources

- [MCP OAuth 2.0 Authentication Guide](MCP_OAUTH_GUIDE.md) — Keycloak setup, tunnel configuration, Claude.ai integration
- [MCP Specification](https://modelcontextprotocol.io) — Official MCP documentation
- [Spring AI MCP Docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) — Spring AI MCP server reference
- [MCP Inspector](https://github.com/modelcontextprotocol/inspector) — Official MCP testing tool
