package dev.mcp.server.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    /**
     * Standalone CorsFilter registered at highest precedence — runs before Spring Security.
     * This ensures:
     *  - OPTIONS preflight requests are resolved before any auth filter sees them.
     *  - CORS headers are present on all responses, including /.well-known/** paths.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterBean() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("WWW-Authenticate", "mcp-session-id"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                    // /error must be permitted so that requests to non-existent /.well-known/**
                    // paths return 404 (not 401). Without this, Spring forwards to /error which
                    // hits anyRequest().authenticated() and the authenticationEntryPoint fires.
                    .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                    // AntPathRequestMatcher bypasses Spring MVC handler lookup,
                    // so it matches /.well-known/** even with no registered controllers.
                    .requestMatchers(new AntPathRequestMatcher("/.well-known/**")).permitAll()
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> {})
                    .authenticationEntryPoint((request, response, authException) -> {

                        // Build the application's base URL from the current request context
                        String base = request.getScheme() + "://" + request.getServerName()
                                + ":" + request.getServerPort();

                        log.info("[Security] 401 WWW-Authenticate base URL={}", base);

                        response.setHeader("WWW-Authenticate",
                                "Bearer resource_metadata=\"" + base + "/.well-known/oauth-protected-resource\"");
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    })
            );
        return http.build();
    }
}
