package dev.mcp.server.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Serves the OAuth Protected Resource Metadata document (RFC 9728).
 *
 * When an MCP client (e.g., Claude.ai) receives a 401 from the /mcp endpoint,
 * it reads the WWW-Authenticate header to find this metadata URL. It then
 * fetches this document to discover which authorization server to use for tokens.
 *
 * Flow:
 *   1. Client → POST /mcp (no token) → 401 with WWW-Authenticate header
 *   2. Client → GET /.well-known/oauth-protected-resource → this endpoint
 *   3. Client reads "authorization_servers" → discovers Okta
 *   4. Client → GET {okta}/.well-known/openid-configuration → discovers token endpoint
 *   5. Client → POST {okta}/v1/token (client_credentials) → gets access token
 *   6. Client → POST /mcp (with Bearer token) → success
 */
@RestController
public class OAuthProtectedResourceController {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String authorizationServer;

    @Value("${scope:profile,email}")
    private String scopes;

    @GetMapping(value = "/.well-known/oauth-protected-resource",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getProtectedResourceMetadata(jakarta.servlet.http.HttpServletRequest request) {

        String resourceUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .build()
                .toUriString();

        return Map.of(
            "resource", resourceUri,
            "authorization_servers", List.of(authorizationServer),
            "scopes_supported", List.of(scopes.split(",")),
            "bearer_methods_supported", List.of("header")
        );
    }

    /**
     * RFC 8414 Authorization Server Metadata — Claude.ai's MCP connector discovers OAuth2
     * endpoints here before trying /.well-known/oauth-protected-resource. Without this,
     * Claude.ai falls back to calling /authorize directly on the MCP server (404).
     *
     * The issuer is this MCP server; the actual authorize/token endpoints proxy to Keycloak.
     */
    @GetMapping(value = "/.well-known/oauth-authorization-server",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getAuthorizationServerMetadata() {

        String mcpBaseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .build()
                .toUriString();

        return Map.of(
            "issuer", mcpBaseUrl,
            "authorization_endpoint", authorizationServer + "/protocol/openid-connect/auth",
            "token_endpoint",         authorizationServer + "/protocol/openid-connect/token",
            "jwks_uri",               authorizationServer + "/protocol/openid-connect/certs",
            "response_types_supported",           List.of("code"),
            "grant_types_supported",              List.of("authorization_code", "refresh_token"),
            "code_challenge_methods_supported",   List.of("S256"),
            "token_endpoint_auth_methods_supported", List.of("none"),
            "scopes_supported",                   List.of(scopes.split(","))
        );
    }
}
 