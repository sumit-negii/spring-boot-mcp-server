# Spring Boot MCP Server — Task Management POC

A production-style proof-of-concept demonstrating how to build a **Model Context Protocol (MCP) server** 
with Spring Boot and integrate it directly with Claude Desktop.

---

## What is MCP?

**Model Context Protocol (MCP)** is an open standard developed by Anthropic that defines how AI models (like Claude) communicate with external tools, data sources, and services. Think of it as a universal plug-in system for AI assistants.

Before MCP, every AI integration required custom, one-off code to connect an LLM to an external system. MCP standardizes this by defining:

- **How tools are described** — each tool has a name, a description, and typed input parameters
- **How tools are called** — via a structured JSON-RPC 2.0 protocol
- **How results are returned** — in a consistent, serializable format

With MCP, an AI model doesn't just generate text — it can take real actions: query a database, create records, update state, search data, and more. The model decides *when* to call a tool and *what arguments* to pass, based on what the user asks.

```
User: "Show me all critical tasks assigned to Bob"
  ↓
Claude decides to call: list_tasks(priority="CRITICAL", assigneeId=2)
  ↓
MCP Server executes the tool and returns results
  ↓
Claude formats the response for the user
```

---

## MCP Architecture

```
┌─────────────────────────────────────┐
│           Claude Desktop            │
│       (AI Model — MCP Client)       │
└────────────────┬────────────────────┘
                 │ stdio (local IPC)
                 ▼
┌─────────────────────────────────────┐
│        mcp-remote (npm bridge)      │
│  Converts stdio ↔ HTTP SSE          │
└────────────────┬────────────────────┘
                 │ HTTP + Server-Sent Events
                 ▼
┌─────────────────────────────────────┐
│   Spring Boot MCP Server (port 8085)│
│                                     │
│  GET  /mcp/sse   ← SSE stream       │
│  POST /mcp/message ← JSON-RPC 2.0  │
│                                     │
│  ┌──────────────────────────────┐   │
│  │  Spring AI MCP Framework     │   │
│  │  Auto-discovers @Tool beans  │   │
│  └──────────────────────────────┘   │
│                                     │
│  TaskTools  ProjectTools  UserTools │
│       ↓            ↓          ↓     │
│         Services + Repositories     │
│              ↓                      │
│         H2 In-Memory Database       │
└─────────────────────────────────────┘
```

### MCP Transport: HTTP + SSE

This project uses the **HTTP with Server-Sent Events (SSE)** transport:

- Claude Desktop connects to `GET /mcp/sse` — an open streaming connection
- Claude sends tool calls via `POST /mcp/message` (JSON-RPC 2.0)
- The server streams results back through the SSE connection

This transport works well for local development and network-accessible servers. The `mcp-remote` npm package acts as a bridge between Claude Desktop's stdio-based client and this HTTP server.

---

## Project Structure

```
src/main/java/dev/springmcp/server/
├── McpServerApplication.java           Main entry point
│
├── config/
│   └── McpToolConfig.java              Registers all tool classes with MCP framework
│
├── domain/                             JPA entities (H2 in-memory database)
│   ├── enums/
│   │   ├── ProjectStatus.java          ACTIVE, ARCHIVED
│   │   ├── TaskPriority.java           LOW, MEDIUM, HIGH, CRITICAL
│   │   ├── TaskStatus.java             TODO, IN_PROGRESS, REVIEW, DONE, CANCELLED
│   │   └── UserRole.java               ADMIN, DEVELOPER, MANAGER
│   ├── Project.java
│   ├── Task.java                       ManyToOne → Project, User
│   └── User.java
│
├── repository/                         Spring Data JPA repositories
│   ├── ProjectRepository.java
│   ├── TaskRepository.java             Custom JPQL keyword search
│   └── UserRepository.java
│
├── service/                            Business logic layer
│   ├── ProjectService.java
│   └── TaskService.java
│
├── tools/                              MCP tool classes (@Tool annotated methods)
│   ├── TaskTools.java                  8 tools
│   ├── ProjectTools.java               5 tools
│   └── UserTools.java                  2 tools
│
└── init/
    └── DataInitializer.java            Seeds sample data on startup
```

---

## Domain Model

This POC implements a **Task Management** system — a real-world domain that demonstrates full CRUD, filtering, search, and relational data via MCP tools.

```
User (1) ──────────── (*) Task (*) ──────────── (1) Project
  id                       id                        id
  name                     title                     name
  email                    description               description
  role                     status                    status
                           priority                  createdAt
                           dueDate
                           createdAt / updatedAt
```

**Sample data seeded on startup:**

| Users | Projects | Tasks |
|-------|----------|-------|
| Alice Chen (MANAGER) | Mobile App Redesign (ACTIVE) | Design new login screen — IN_PROGRESS, HIGH |
| Bob Smith (DEVELOPER) | API Platform v2 (ACTIVE) | Implement JWT authentication — TODO, CRITICAL |
| Carol Davis (DEVELOPER) | Legacy System Migration (ARCHIVED) | Write API documentation — TODO, MEDIUM |
| Dave Wilson (ADMIN) | | Set up CI/CD pipeline — REVIEW, HIGH |
| | | Performance testing — TODO, MEDIUM |
| | | Database schema audit — DONE, LOW |
| | | Fix null pointer in user profile — TODO, CRITICAL |

---

## MCP Tools Reference

### Task Tools (8)

| Tool | Description | Key Parameters |
|------|-------------|----------------|
| `create_task` | Create a new task | `title`, `priority`, `dueDate`, `projectId`, `assigneeId` |
| `list_tasks` | List tasks with filters | `status`, `priority`, `projectId`, `assigneeId` (all optional) |
| `get_task` | Get task by ID | `taskId` |
| `update_task` | Partial update task fields | `taskId`, any of `title`, `description`, `priority`, `dueDate` |
| `delete_task` | Delete a task | `taskId` |
| `assign_task` | Assign task to a user | `taskId`, `userId` |
| `update_task_status` | Change task status | `taskId`, `status` |
| `search_tasks` | Full-text search in title + description | `keyword` |

### Project Tools (5)

| Tool | Description | Key Parameters |
|------|-------------|----------------|
| `create_project` | Create a project (starts ACTIVE) | `name`, `description` |
| `list_projects` | List all projects | `status` (optional) |
| `get_project_with_tasks` | Project + all its tasks | `projectId` |
| `update_project` | Partial update project fields | `projectId`, any of `name`, `description`, `status` |
| `delete_project` | Delete a project | `projectId` |

### User Tools (2)

| Tool | Description | Key Parameters |
|------|-------------|----------------|
| `list_users` | List all users | `role` (optional) |
| `get_user_tasks` | User profile + assigned tasks | `userId` |

---

## How Spring AI Registers MCP Tools

Spring AI's MCP framework scans for `ToolCallbackProvider` beans and automatically exposes them via the SSE transport. The `@Tool` annotation on a method declares it as an MCP tool, and `@ToolParam` documents each parameter for the AI model.

```java
// McpToolConfig.java — single bean registers all 15 tools
@Bean
public ToolCallbackProvider taskManagementToolCallbackProvider(
        TaskTools taskTools, ProjectTools projectTools, UserTools userTools) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(taskTools, projectTools, userTools)
            .build();
}
```

```java
// Example tool method in TaskTools.java
@Tool(name = "list_tasks",
      description = "List tasks with optional filters for status, priority, project, and assignee.")
public List<Task> listTasks(
        @ToolParam(description = "Filter by status: TODO, IN_PROGRESS, REVIEW, DONE, CANCELLED", required = false) String status,
        @ToolParam(description = "Filter by priority: LOW, MEDIUM, HIGH, CRITICAL", required = false) String priority,
        @ToolParam(description = "Filter by project ID", required = false) Long projectId,
        @ToolParam(description = "Filter by assignee user ID", required = false) Long assigneeId) {
    // ...
}
```

The framework generates a JSON Schema from these annotations and sends it to Claude as part of the MCP `initialize` handshake. Claude uses this schema to know what tools exist and how to call them.

---

## Getting Started

### Prerequisites

- Java 21+
- Node.js (for `npx` / `mcp-remote`)
- Claude Desktop

### Run the Server

```bash
./gradlew bootRun
```

The server starts on **port 8085** and seeds sample data automatically. You should see:

```
[DataInitializer] Sample data created: 4 users, 3 projects, 7 tasks
Tomcat started on port 8085
```

### Connect Claude Desktop

Edit `%APPDATA%\Claude\claude_desktop_config.json` (Windows) or
`~/Library/Application Support/Claude/claude_desktop_config.json` (macOS):

```json
{
  "mcpServers": {
    "task-management": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "http://localhost:8085/sse"
      ]
    }
  }
}
```

Restart Claude Desktop. You will see the tool icon appear in the chat interface, confirming the MCP server is connected.

### Try These Prompts

```
"List all projects"
"Show me all CRITICAL priority tasks"
"What tasks is Bob working on?"
"Create a task called 'Update dependencies' in project 2, assign it to Carol, due next Friday"
"Mark task 3 as done"
"Search for tasks related to authentication"
"Show me the Mobile App Redesign project with all its tasks"
```

### Debug via H2 Console

While the server is running, open:
```
http://localhost:8085/h2-console
```
JDBC URL: `jdbc:h2:mem:taskdb` | Username: `sa` | Password: _(empty)_

---

## Key Technology Choices

| Technology | Version | Role |
|------------|---------|------|
| Spring Boot | 3.3.5 | Application framework |
| Spring AI MCP Server | 1.0.0 | MCP protocol implementation |
| Spring Data JPA | (via Boot) | Database abstraction layer |
| H2 Database | (via Boot) | In-memory database for POC |
| Lombok | (via Boot) | Boilerplate reduction |
| Java | 21 | Language (records, pattern matching) |
| Gradle | 8.13 | Build tool |

---

## Future Scope

### 1. Persistent Database
Replace H2 in-memory with PostgreSQL or MySQL. Change `spring.datasource` and `ddl-auto: validate`. Add Flyway or Liquibase for schema migrations.

### 2. Stdio Transport
Switch from HTTP SSE to stdio transport for native Claude Desktop integration without the `mcp-remote` bridge. Spring AI supports stdio via `spring-ai-starter-mcp-server-stdio`.

```json
{
  "mcpServers": {
    "task-management": {
      "command": "java",
      "args": ["-jar", "spring-boot-mcp-server.jar"]
    }
  }
}
```

### 3. Authentication & Multi-Tenancy
Add Spring Security with JWT or OAuth2 to secure MCP endpoints. Scope data per tenant so each user or organization only sees their own projects and tasks.

### 4. MCP Resources
Beyond tools, MCP supports **Resources** — structured data the AI can read (like reading a file or fetching a record). Expose task details, project summaries, and user workloads as MCP resources for richer context injection.

### 5. MCP Prompts
Register reusable **Prompt templates** via MCP so users can invoke pre-built workflows — e.g., a "daily standup" prompt that automatically fetches all in-progress tasks and formats them as a standup report.

### 6. Real-Time Notifications with SSE Push
Currently SSE is used only for MCP protocol messages. Extend it to push task update notifications to connected clients — e.g., notify Claude when a task's status changes externally.

### 7. More Domain Entities
Extend the domain with:
- **Comments** on tasks (threading, mentions)
- **Labels / Tags** for flexible categorization
- **Sprints / Milestones** for time-boxed planning
- **Time Tracking** (logged hours per task)

### 8. Tool Result Caching
Add a caching layer (Spring Cache + Caffeine) for read-heavy tools like `list_projects` and `list_users` to reduce database load in a high-concurrency deployment.

### 9. Observability
Add structured logging (Logstash/JSON format), metrics (Micrometer + Prometheus), and distributed tracing (OpenTelemetry) to monitor which MCP tools are called, how often, and how long they take.

### 10. Containerization
Package with Docker for portable deployment:
```dockerfile
FROM eclipse-temurin:21-jre
COPY build/libs/spring-boot-mcp-server.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## MCP Protocol Flow (Technical Detail)

```
1. INITIALIZE
   Claude → server: { method: "initialize", params: { clientInfo, capabilities } }
   Server → Claude: { result: { serverInfo, capabilities: { tools: {} } } }

2. TOOLS/LIST
   Claude → server: { method: "tools/list" }
   Server → Claude: { result: { tools: [ { name, description, inputSchema }, ... ] } }
   (All 15 tools are listed with their JSON Schema descriptions)

3. TOOLS/CALL (when user asks a question that requires a tool)
   Claude → server: { method: "tools/call", params: { name: "list_tasks", arguments: { status: "TODO" } } }
   Server → Claude: { result: { content: [ { type: "text", text: "[{...task JSON...}]" } ] } }

4. Claude formats the tool result and responds to the user
```

---

## Resources

- [MCP Specification](https://modelcontextprotocol.io) — Official MCP documentation
- [Spring AI MCP Docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) — Spring AI MCP server integration
- [Claude Desktop MCP Guide](https://modelcontextprotocol.io/quickstart/user) — How to connect MCP servers to Claude Desktop
