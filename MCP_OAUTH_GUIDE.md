# MCP OAuth 2.0 Authentication Guide

## Table of Contents
1. [The Authentication Process — What It Is and How It Works](#1-the-authentication-process)
2. [Keycloak — The Authorization Server](#2-keycloak-the-authorization-server)
3. [Infrastructure Setup — Tunnels](#3-infrastructure-setup-tunnels)
4. [Testing with Claude.ai](#4-testing-with-claudeai)
5. [Testing with MCP Inspector](#5-testing-with-mcp-inspector)
6. [Troubleshooting Reference](#6-troubleshooting-reference)

---

## 1. The Authentication Process

### 1.1 What is it called?

The pattern implemented here is:

> **OAuth 2.0 Authorization Code Flow with PKCE**
> (Proof Key for Code Exchange — RFC 7636)

It is implemented inside the **Model Context Protocol (MCP) OAuth specification**, which standardizes how MCP clients authenticate with MCP servers. The full specification draws from:

| RFC           | Purpose                                                                     |
|---------------|-----------------------------------------------------------------------------|
| RFC 6749      | Core OAuth 2.0 framework                                                    |
| RFC 7636      | PKCE — eliminates the need for a client secret                              |
| RFC 8414      | Authorization Server Metadata — how clients discover OAuth endpoints        |
| RFC 9728      | Protected Resource Metadata — how clients discover which auth server to use |
| MCP 2025 spec | How MCP clients and servers negotiate auth                                  |

---

### 1.2 Why PKCE Instead of a Client Secret?

Standard OAuth 2.0 requires a `client_secret` to exchange the authorization code for a token. This is safe for server-side apps but **unsafe for public clients** (browser apps, CLI tools, MCP clients) because:
- The secret must be shipped with the client
- Anyone who extracts it can impersonate the client

**PKCE replaces the secret with a one-time cryptographic challenge:**

```
Client generates:
  code_verifier  = random 43-128 character string  (kept secret, never sent first)
  code_challenge = BASE64URL(SHA256(code_verifier)) (sent in the authorization request)

Authorization request:
  GET /authorize?...&code_challenge=<hash>&code_challenge_method=S256

Token exchange:
  POST /token
    code=<auth-code>&code_verifier=<original-string>

Auth server verifies:
  SHA256(code_verifier) == code_challenge  →  token issued
```

Even if an attacker intercepts the authorization code, they cannot exchange it without the `code_verifier`, which was never transmitted.

---

### 1.3 Roles in This Architecture

```
┌───────────────────────┐
│     MCP Client        │   Claude.ai or MCP Inspector
│     (OAuth Client)    │   Initiates the OAuth flow, holds the access token
└───────────────────────┘

┌───────────────────────┐
│   Spring Boot App     │   This project
│   (Resource Server)   │   Validates JWTs, exposes MCP tools
└───────────────────────┘

┌───────────────────────┐
│      Keycloak         │   External authorization server
│  (Auth Server)        │   Issues JWTs, manages users and clients
└───────────────────────┘
```

The MCP server **never handles login itself**. It only:
1. Rejects unauthenticated requests with a `401`
2. Tells clients where to authenticate (via discovery endpoints)
3. Validates the JWT on every authenticated request

---

### 1.4 The Full OAuth Flow Step by Step

```
MCP Client                    Spring Boot MCP Server              Keycloak
     │                                   │                            │
     │── POST /mcp (no token) ──────────>│                            │
     │                                   │                            │
     │<── 401 Unauthorized ──────────────│                            │
     │    WWW-Authenticate:              │                            │
     │    Bearer resource_metadata=      │                            │
     │    "https://.../                  │                            │
     │     .well-known/oauth-            │                            │
     │     protected-resource"           │                            │
     │                                   │                            │
     │── GET /.well-known/               │                            │
     │   oauth-authorization-server ────>│                            │
     │                                   │                            │
     │<── { issuer, ─────────────────────│                            │
     │     authorization_endpoint:       │                            │
     │       keycloak/auth,              │                            │
     │     token_endpoint:               │                            │
     │       keycloak/token }            │                            │
     │                                   │                            │
     │── GET keycloak/authorize? ─────────────────────────────────>   │
     │   response_type=code              │                            │
     │   client_id=...                   │                            │
     │   code_challenge=<hash>           │                            │
     │   redirect_uri=<callback>         │                            │
     │                                   │                            │
     │<── Keycloak Login Page ──────────────────────────────────────  │
     │                                   │                            │
     │    [User enters credentials]      │                            │
     │                                   │                            │
     │── POST credentials ────────────────────────────────────────>   │
     │                                   │                            │
     │<── 302 Redirect to callback ─────────────────────────────────  │
     │    ?code=<auth-code>              │                            │
     │    &state=...                     │                            │
     │                                   │                            │
     │── POST keycloak/token ──────────────────────────────────────>  │
     │   grant_type=authorization_code   │                            │
     │   code=<auth-code>                │                            │
     │   code_verifier=<original>        │                            │
     │   client_id=...                   │                            │
     │                                   │                            │
     │<── { access_token: <JWT> } ─────────────────────────────────   │
     │                                   │                            │
     │── POST /mcp ─────────────────────>│                            │
     │   Authorization: Bearer <JWT>     │                            │
     │                                   │── validate JWT ──────────> │
     │                                   │   (checks signature,       │
     │                                   │    expiry, issuer)         │
     │                                   │<─ valid ─────────────────  │
     │                                   │                            │
     │<── MCP Tool Response ─────────────│                            │
```

---

### 1.5 The Two Discovery Endpoints This Server Exposes

MCP clients use these endpoints to find the authorization server without any hardcoded URLs.

**`GET /.well-known/oauth-authorization-server`** — RFC 8414 (Primary)
```json
{
  "issuer": "https://mcp-server-url",
  "authorization_endpoint": "https://keycloak/realms/.../protocol/openid-connect/auth",
  "token_endpoint": "https://keycloak/realms/.../protocol/openid-connect/token",
  "jwks_uri": "https://keycloak/realms/.../protocol/openid-connect/certs",
  "code_challenge_methods_supported": ["S256"],
  "response_types_supported": ["code"],
  "token_endpoint_auth_methods_supported": ["none"]
}
```
Claude.ai checks this endpoint first. If it returns 404, Claude.ai falls back to calling `/authorize` on the MCP server itself — which would fail. This endpoint is what makes the full flow work.

**`GET /.well-known/oauth-protected-resource`** — RFC 9728 (Fallback)
```json
{
  "resource": "https://mcp-server-url",
  "authorization_servers": ["https://keycloak/realms/mcp-task-orchestrator-realm"],
  "scopes_supported": ["profile", "email"],
  "bearer_methods_supported": ["header"]
}
```
Used by clients that implement the resource server discovery spec.

**`WWW-Authenticate` header on every 401:**
```
WWW-Authenticate: Bearer resource_metadata="https://mcp-server/.well-known/oauth-protected-resource"
```

---

### 1.6 JWT Validation on Every Request

After the initial login, every MCP request carries a JWT in the `Authorization: Bearer` header. Spring Boot validates this token on every request without calling Keycloak:

1. Fetch Keycloak's public keys from `jwks_uri` (cached, refreshed automatically)
2. Verify the JWT signature using those public keys
3. Check the `iss` (issuer) claim matches the configured `issuer-uri`
4. Check the `exp` (expiry) claim — token must not be expired

If all checks pass, the request proceeds to the MCP tool handler.

---

## 2. Keycloak — The Authorization Server

### 2.1 Why Keycloak?

Keycloak is an open-source Identity and Access Management server. In this project it acts as the **Authorization Server** — the entity that authenticates users, manages clients, and issues signed JWTs.

It is a direct replacement for cloud-based identity providers like Okta, Auth0, or Azure AD, with the advantage of running fully locally.

### 2.2 Install and Start Keycloak

```bash
docker run -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  --name keycloak \
  quay.io/keycloak/keycloak:latest start-dev
```

Access:
- **Admin Console:** `http://localhost:8080/admin` — username: `admin`, password: `admin`

### 2.3 Create the Realm

1. Open Admin Console → click realm dropdown (top-left) → **Create Realm**
2. **Realm name:** `mcp-task-orchestrator-realm`
3. Click **Create**

### 2.4 Create a Test User

1. Select realm `mcp-task-orchestrator-realm`
2. **Users** → **Add user**
3. Set **Username**, **Email**, toggle **Email verified** ON
4. Click **Create**
5. Go to **Credentials** tab → **Set password** → enter a password, disable **Temporary**

---

## 3. Infrastructure Setup — Tunnels

### Why Two Tunnels?

Two services need to be publicly accessible on separate URLs:

| Service | Why it must be public |
|---|---|
| **MCP server (8085)** | Claude.ai calls this from Anthropic's infrastructure |
| **Keycloak (8080)** | Claude.ai's **backend** calls the token endpoint to exchange the auth code |

> **MCP Inspector:** If you only use MCP Inspector (no Claude.ai), everything runs locally. No tunnels needed. Skip to Section 5.

### 3.1 ngrok — For the MCP Server (Port 8085)

ngrok free tier includes one static domain. Assign it to the MCP server so the Claude.ai connector URL never changes.

**One-time config — edit `%USERPROFILE%\AppData\Local\ngrok\ngrok.yml`:**
```yaml
version: "3"
agent:
  authtoken: <your-ngrok-authtoken>

tunnels:
  mcp-server:
    proto: http
    addr: 8085
    domain: <your-static-domain>.ngrok-free.app
```

**Start the tunnel:**
```powershell
ngrok start mcp-server
```

Your MCP server public URL: `https://<your-static-domain>.ngrok-free.app`

### 3.2 Cloudflare Quick Tunnel — For Keycloak (Port 8080)

Cloudflare quick tunnels are free, require no account, and give a random URL each session.

**Install once:**
```powershell
winget install --id Cloudflare.cloudflared
```

**Start each session (new terminal):**
```powershell
cloudflared tunnel --url http://localhost:8080
```

Note the URL it prints, e.g. `https://particular-olive-neon.trycloudflare.com`

> **Important:** This URL changes every time you restart the Cloudflare tunnel. You must update two things each new session (steps covered in Section 4).

---

## 4. Testing with Claude.ai

### 4.1 Keycloak Client Setup for Claude.ai

This client is a **public client** — no secret, uses PKCE.

1. Keycloak Admin → realm `mcp-task-orchestrator-realm` → **Clients** → **Create client**

2. **General Settings tab:**
   | Field | Value |
   |---|---|
   | Client type | OpenID Connect |
   | Client ID | `claude-ai-connector` |

3. **Capability Config tab:**
   | Field | Value |
   |---|---|
   | Require PKCE  | ON |


4. **Login Settings tab:**
   | Field | Value |
   |---|---|
   | Valid redirect URIs | `https://claude.ai/api/mcp/auth_callback` |
   | Web origins | `https://claude.ai` |

   > Use the exact redirect URI — wildcards can fail during token exchange.

5. Click **Save**

---

### 4.2 Session Startup Checklist (Claude.ai)

Perform these steps at the start of every new development session.

**Step 1 — Start Keycloak** (if not already running)
```powershell
docker start keycloak
```

**Step 2 — Start the MCP server ngrok tunnel**
```powershell
ngrok start mcp-server
```
URL stays the same: `https://<your-static-domain>.ngrok-free.app`

**Step 3 — Start the Cloudflare tunnel for Keycloak**
```powershell
cloudflared tunnel --url http://localhost:8080
```
Note the new Cloudflare URL, e.g. `https://abc-xyz.trycloudflare.com`

**Step 4 — Update Keycloak Frontend URL**

This makes Keycloak embed the Cloudflare URL in JWTs as the `iss` claim.

1. Keycloak Admin → realm `mcp-task-orchestrator-realm` → **Realm Settings**
2. **General tab** → **Frontend URL**
3. Set to: `https://abc-xyz.trycloudflare.com`
4. **Save**

**Step 5 — Start the MCP Server**
```powershell
$env:KEYCLOAK_ISSUER_URI = "https://abc-xyz.trycloudflare.com/realms/mcp-task-orchestrator-realm"
./gradlew bootRun
```

**Step 6 — Verify the Discovery Chain**

Run these checks before connecting Claude.ai:
```powershell
# Should return 401 with WWW-Authenticate header
curl -i https://<your-static-domain>.ngrok-free.app/mcp

# authorization_endpoint and token_endpoint MUST show Cloudflare URL, not localhost
curl https://<your-static-domain>.ngrok-free.app/.well-known/oauth-authorization-server

# authorization_servers MUST show Cloudflare URL
curl https://<your-static-domain>.ngrok-free.app/.well-known/oauth-protected-resource
```

If any URL shows `localhost`, the env var was not picked up — restart the MCP server.

---

### 4.3 Add the Claude.ai Connector

1. Go to [claude.ai](https://claude.ai) → **Customize** → **Connectors**
2. Click **Add custom connector**
3. Fill in:
   | Field | Value |
   |---|---|
   | Name | MCP Task Orchestrator |
   | Server URL | `https://<your-static-domain>.ngrok-free.app/mcp` |
   | Client ID | `claude-ai-connector` |

   > No client secret — it is a public client.

4. Click **Connect**
5. You are redirected to the Keycloak login page
6. Log in with your Keycloak test user credentials
7. On success, you are returned to Claude.ai with the connector active

---

### 4.4 Test the Claude.ai Connector

Start a new conversation and ask Claude to use the tools:

**Browse data:**
- "List all tasks"
- "Show me all projects"
- "List all users"
- "Show me tasks assigned to Alice"

**Create data:**
- "Create a new project called 'Q3 Launch' with description 'Summer release'"
- "Create a HIGH priority task called 'Fix login bug' in the Mobile App project"

**Update data:**
- "Assign task 1 to Bob"
- "Change the status of task 2 to IN_PROGRESS"
- "Update the priority of task 3 to CRITICAL"

**Search:**
- "Search for tasks containing the word 'API'"
- "Show me all DONE tasks"

Claude will call the appropriate MCP tools (defined in `TaskTools`, `ProjectTools`, `UserTools`) and return the results inline in the conversation.

---

### 4.5 Re-authorizing After a Session Restart

If you restart the Cloudflare tunnel (new URL), you must:
1. Update Keycloak Frontend URL (Step 4 of the checklist)
2. Restart the MCP server with the new env var (Step 5)
3. In Claude.ai → Settings → Integrations → find the connector → **Reconnect** or **Reauthorize**

The ngrok MCP server URL stays the same, so you do not need to update the connector URL.

---

## 5. Testing with MCP Inspector

> MCP Inspector is a browser-based tool for testing MCP servers directly. It runs entirely on your machine — no tunnels are required for local testing.

### 5.1 Keycloak Client Setup for MCP Inspector

1. Keycloak Admin → realm `mcp-task-orchestrator-realm` → **Clients** → **Create client**

2. **General Settings tab:**
   | Field | Value |
   |---|---|
   | Client type | OpenID Connect |
   | Client ID | `mcp-inspector` |

3. **Capability Config tab:**
   | Field | Value |
   |---|---|
   | Require PKCE  | ON |

4. **Login Settings tab:**
   | Field | Value |
   |---|---|
   | Valid redirect URIs | `http://localhost:6274/oauth/callback` |
   | Web origins | `http://localhost:6274` |

   > The redirect URI is localhost because MCP Inspector runs locally.

5. Click **Save**

---

### 5.2 Session Startup for MCP Inspector (Local Only)

When testing with MCP Inspector only (no Claude.ai), you can run everything locally — no tunnels needed.

**Step 1 — Start Keycloak**
```powershell
docker start keycloak
```

**Step 2 — Start the MCP Server**

If you are running only MCP Inspector (not Claude.ai at the same time), use the default localhost issuer:
```powershell
# No env var needed — defaults to http://localhost:8080
./gradlew bootRun
```

If you are running MCP Inspector alongside Claude.ai (tunnels already running):
```powershell
# Use the same Cloudflare URL already set for Claude.ai
$env:KEYCLOAK_ISSUER_URI = "https://abc-xyz.trycloudflare.com/realms/mcp-task-orchestrator-realm"
./gradlew bootRun
```

> **Note:** If Keycloak's Frontend URL is set to the Cloudflare URL (for Claude.ai), you must also use that Cloudflare URL as the auth server in MCP Inspector — the JWT `iss` claim will be the Cloudflare URL, and Spring Boot must match it.

---

### 5.3 Run MCP Inspector

No installation required:
```powershell
npx @modelcontextprotocol/inspector
```

Opens at: `http://localhost:6274`

---

### 5.4 Connect to the MCP Server

In the MCP Inspector UI:

**If running locally (no Claude.ai tunnels):**
| Field | Value |
|---|---|
| Transport | Streamable HTTP |
| URL | `http://localhost:8085/mcp` |
| Connection Type | `Direct` |
| OAuth Client ID | `mcp-inspector` |
|

Click **Connect** — a browser tab opens to the Keycloak login page. Log in with your Keycloak test user. On success, MCP Inspector shows the connected state.

---

### 5.5 Test MCP Tools in Inspector

After connecting, use the Inspector's **Tools** tab to browse and call tools.

**List all tools** — click the refresh/fetch button to load available tools.

**Call `list_tasks`** (no parameters):
```json
{}
```

**Call `create_task`:**
```json
{
  "title": "Fix login page",
  "description": "Button misaligned on mobile",
  "priority": "HIGH"
}
```

**Call `get_task`:**
```json
{
  "id": 1
}
```

**Call `update_task_status`:**
```json
{
  "id": 1,
  "status": "IN_PROGRESS"
}
```

**Call `assign_task`:**
```json
{
  "taskId": 1,
  "userId": 2
}
```

**Call `search_tasks`:**
```json
{
  "keyword": "login"
}
```

**Call `list_projects`:**
```json
{}
```

**Call `create_project`:**
```json
{
  "name": "New Feature Sprint",
  "description": "Sprint 14 work items"
}
```

**Call `list_users`:**
```json
{}
```

**Call `get_user_tasks`:**
```json
{
  "userId": 1
}
```

---

### 5.6 Sending Requests via MCP Java Client

If you are using the Spring AI MCP client (`spring-ai-starter-mcp-client`) in a separate Spring Boot application:

**application.yml of the client project:**
```yaml
spring:
  ai:
    mcp:
      client:
        toolcallback:
          enabled: true
        connections:
          task-server:
            transport: STREAMABLE_HTTP
            url: http://localhost:8085/mcp
            sse-endpoint: /mcp
```

**OAuth2 token — auth server discovery method:**

The MCP client fetches `/.well-known/oauth-authorization-server` from the MCP server URL, discovers the Keycloak token endpoint, and requests a token using Client Credentials grant (for machine-to-machine) or passes a pre-obtained token.

For testing with a pre-obtained token:
```powershell
# 1. Get a token from Keycloak (using password grant for testing only)
$response = Invoke-RestMethod `
  -Uri "http://localhost:8080/realms/mcp-task-orchestrator-realm/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=mcp-inspector&username=testuser&password=<password>"

$token = $response.access_token

# 2. Call the MCP server directly
curl -X POST http://localhost:8085/mcp `
  -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" `
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

---

## 6. Troubleshooting Reference

| Symptom | Root Cause | Fix |
|---|---|---|
| Claude.ai calls `<mcp-url>/authorize` → 404 | `/.well-known/oauth-authorization-server` missing | Already implemented in this project — verify the endpoint returns 200 |
| `authorization_endpoint` shows `localhost` | `KEYCLOAK_ISSUER_URI` env var not set or app not restarted | Set the env var before `./gradlew bootRun` |
| Token exchange fails — "unable to find matching target resource method" | Two services share the same ngrok URL (pooling) | Use separate tunnel URLs — one per service |
| Authorization fails after login | Redirect URI mismatch in Keycloak | Set exact URI: `https://claude.ai/api/mcp/auth_callback` |
| JWT validation fails — "iss mismatch" | Keycloak Frontend URL not updated after new Cloudflare session | Update Realm Settings → Frontend URL to the new Cloudflare URL |
| MCP Inspector login redirects to wrong Keycloak | Auth URL in Inspector still points to old Cloudflare URL | Update the Authorization URL field in Inspector |
| 401 on every MCP call despite valid token | `issuer-uri` in Spring Boot still `localhost` | Restart with the correct `KEYCLOAK_ISSUER_URI` env var |
| Keycloak login page appears then immediately errors | PKCE not configured on the Keycloak client | Advanced tab → set PKCE Code Challenge Method to **S256** |
| ngrok "endpoint already online" error | Static domain already in use by another tunnel | Stop the existing tunnel first: `ngrok start mcp-server` (not `--all`) |

---

## Quick Reference

### URLs at a Glance

| Service | Local URL | Public URL |
|---|---|---|
| MCP Server endpoint | `http://localhost:8085/mcp` | `https://<ngrok-domain>/mcp` |
| RFC 8414 discovery | `http://localhost:8085/.well-known/oauth-authorization-server` | `https://<ngrok-domain>/.well-known/oauth-authorization-server` |
| RFC 9728 discovery | `http://localhost:8085/.well-known/oauth-protected-resource` | `https://<ngrok-domain>/.well-known/oauth-protected-resource` |
| Keycloak admin | `http://localhost:8080/admin` | — |
| Keycloak authorize | `http://localhost:8080/realms/mcp-task-orchestrator-realm/protocol/openid-connect/auth` | `https://<cloudflare-url>/realms/mcp-task-orchestrator-realm/protocol/openid-connect/auth` |
| Keycloak token | `http://localhost:8080/realms/mcp-task-orchestrator-realm/protocol/openid-connect/token` | `https://<cloudflare-url>/realms/mcp-task-orchestrator-realm/protocol/openid-connect/token` |

### Keycloak Clients Summary

| Client ID | Use Case | Redirect URI | PKCE |
|---|---|---|---|
| `claude-ai-connector` | Claude.ai MCP connector | `https://claude.ai/api/mcp/auth_callback` | S256 |
| `mcp-inspector` | MCP Inspector (local testing) | `http://localhost:6274/oauth/callback` | S256 |

### Environment Variable

```powershell
# Set before starting the MCP server when using tunnels
$env:KEYCLOAK_ISSUER_URI = "https://<cloudflare-url>/realms/mcp-task-orchestrator-realm"
```

Default (when not set): `http://localhost:8080/realms/mcp-task-orchestrator-realm`
