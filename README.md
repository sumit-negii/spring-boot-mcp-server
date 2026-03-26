# Spring Boot MCP Server — Task Management POC

A proof-of-concept demonstrating how to build a **Model Context Protocol (MCP) server** with Spring Boot and connect it to Claude Desktop or the MCP Inspector.

---

## What is MCP?

**Model Context Protocol (MCP)** is an open standard by Anthropic that lets AI models (like Claude) communicate with external tools and data sources in a standardized way. Think of it as a universal plugin system for AI.

With MCP, Claude doesn't just generate text — it can take real actions:

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

## MCP Transport: What It Is and Which to Use

A **transport** is simply how the MCP client (Claude) and the MCP server talk to each other. There are three options:

### STDIO
The client launches the server as a subprocess and communicates via standard input/output pipes.
- **Good for:** local desktop tools, IDE plugins
- **Not good for:** deployed/shared servers, Docker, remote access

### HTTP + SSE *(deprecated)*
Uses two HTTP endpoints — one for streaming events (`GET /sse`) and one for sending commands (`POST /message`). Requires a bridge tool (`mcp-remote`) between Claude Desktop and the server.
- **Introduced:** November 2024
- **Deprecated:** March 2025 — replaced by Streamable HTTP

### Streamable HTTP *(current standard — what this project uses)*
A single `POST /mcp` endpoint handles everything. The server replies with plain JSON for simple responses, or streams SSE when needed. No bridge tool required for direct HTTP clients.
- **Introduced:** March 2025 (MCP spec 2025-03-26)
- **Benefits:** single endpoint, works with load balancers and proxies, simpler setup

> **This project uses Streamable HTTP.** It is the current MCP standard and the recommended transport for all new servers.

---

## Architecture

```
┌─────────────────────────────────────┐
│           Claude Desktop            │
│       (AI Model — MCP Client)       │
└────────────────┬────────────────────┘
                 │ stdio
                 ▼
┌─────────────────────────────────────┐
│        mcp-remote (npm bridge)      │
│  Adapts Claude Desktop stdio        │
│  to Streamable HTTP                 │
└────────────────┬────────────────────┘
                 │ HTTP (Streamable HTTP)
                 ▼
┌─────────────────────────────────────┐
│   Spring Boot MCP Server            │
│   port 8085                         │
│                                     │
│   POST /mcp  ← single endpoint      │
│                                     │
│   TaskTools  ProjectTools UserTools │
│        ↓           ↓          ↓     │
│      Services + Repositories        │
│              ↓                      │
│      H2 In-Memory Database          │
└─────────────────────────────────────┘
```

> Claude Desktop only speaks stdio natively, so `mcp-remote` acts as a thin bridge. The MCP Inspector connects directly via HTTP — no bridge needed.

---

## Project Structure

```
src/main/java/dev/mcp/server/
├── McpServerApplication.java           Main entry point
│
├── config/
│   └── McpToolConfig.java              Registers all tools with MCP framework
│
├── domain/                             JPA entities
│   ├── enums/
│   │   ├── ProjectStatus.java          ACTIVE, ARCHIVED
│   │   ├── TaskPriority.java           LOW, MEDIUM, HIGH, CRITICAL
│   │   ├── TaskStatus.java             TODO, IN_PROGRESS, REVIEW, DONE, CANCELLED
│   │   └── UserRole.java               ADMIN, DEVELOPER, MANAGER
│   ├── Project.java
│   ├── Task.java
│   └── User.java
│
├── repository/                         Spring Data JPA repositories
│   ├── ProjectRepository.java
│   ├── TaskRepository.java             
│   └── UserRepository.java
│
├── service/                            Business logic
│   ├── ProjectService.java
│   └── TaskService.java
│
├── tools/                              MCP tool methods (@Tool annotated)
│   ├── TaskTools.java                  8 tools
│   ├── ProjectTools.java               5 tools
│   └── UserTools.java                  2 tools
│
└── init/
    └── DataInitializer.java            Seeds sample data on startup
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
- **Node.js 18+** — [Download here](https://nodejs.org) *(required for mcp-remote and MCP Inspector)*
- **Claude Desktop** *(optional — only needed to connect to Claude)*

> After installing Node.js, verify it works: `node --version` and `npx --version`

### 1. Start the Server

```bash
./gradlew bootRun
```

The server starts on **port 8085** and seeds sample data automatically:

```
[DataInitializer] Sample data created: 4 users, 3 projects, 7 tasks
Tomcat started on port(s): 8085
```

Sample data includes:
- **Users:** Alice (MANAGER), Bob (DEVELOPER), Carol (DEVELOPER), Dave (ADMIN)
- **Projects:** Mobile App Redesign, API Platform v2, Legacy System Migration
- **Tasks:** 7 tasks with mixed priorities and statuses

---

## Connect via MCP Inspector *(easiest way to test)*

The **MCP Inspector** is an official browser-based tool to explore and test any MCP server. No Claude Desktop needed.

### Run the Inspector

```bash
npx @modelcontextprotocol/inspector
```

This opens the Inspector UI at **http://localhost:6274**

### Connect to this server

In the Inspector UI:

| Field | Value |
|-------|-------|
| Transport | Streamable HTTP |
| URL | `http://localhost:8085/mcp` |

Click **Connect** — you will see all 15 tools listed. You can call any tool directly from the UI and see the raw JSON response.

---

## Connect via Claude Desktop

### Step 1 — Edit the config file

Open the Claude Desktop config file:

- **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`
- **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`

### Step 2 — Add the server entry

```json
{
  "mcpServers": {
    "task-management": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "http://localhost:8085/mcp"
      ]
    }
  }
}
```

### Step 3 — Restart Claude Desktop

After restarting, you will see the tool icon (hammer) in the chat interface. The MCP server is connected.

### Try these prompts

```
"List all projects"
"Show me all CRITICAL tasks"
"What tasks is Bob working on?"
"Create a task called 'Update dependencies' in project 2, assign it to Carol"
"Mark task 3 as done"
"Search for tasks related to authentication"
"Show me the Mobile App Redesign project with all its tasks"
```

---

## Debug via H2 Console

While the server is running, open:

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

Spring AI scans for `ToolCallbackProvider` beans and exposes them automatically via the MCP transport. The `@Tool` annotation marks a method as an MCP tool.

```java
// McpToolConfig.java — registers all 15 tools in one place
@Bean
public ToolCallbackProvider taskManagementToolCallbackProvider(
        TaskTools taskTools, ProjectTools projectTools, UserTools userTools) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(taskTools, projectTools, userTools)
            .build();
}
```

```java
// Example tool in TaskTools.java
@Tool(name = "list_tasks", description = "List tasks with optional filters.")
public List<Task> listTasks(
        @ToolParam(description = "Filter by status: TODO, IN_PROGRESS, REVIEW, DONE, CANCELLED", required = false) String status,
        @ToolParam(description = "Filter by priority: LOW, MEDIUM, HIGH, CRITICAL", required = false) String priority) {
    // ...
}
```

Spring AI generates a JSON Schema from these annotations and sends it to Claude during the MCP handshake, so Claude knows exactly what tools exist and how to call them.

---

## Tech Stack

| Technology | Version | Role |
|------------|---------|------|
| Spring Boot | 3.4.3 | Application framework |
| Spring AI MCP Server | 1.1.3 | MCP protocol + Streamable HTTP transport |
| Spring Data JPA | via Boot | Database abstraction |
| H2 Database | via Boot | In-memory database |
| Lombok | via Boot | Boilerplate reduction |
| Java | 21 | Language |
| Gradle | 8.13 | Build tool |

---

## Resources

- [MCP Specification](https://modelcontextprotocol.io) — Official MCP documentation
- [Spring AI MCP Docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) — Spring AI MCP server reference
- [MCP Inspector](https://github.com/modelcontextprotocol/inspector) — Official MCP testing tool
- [Claude Desktop MCP Guide](https://modelcontextprotocol.io/quickstart/user) — Connecting MCP servers to Claude Desktop
