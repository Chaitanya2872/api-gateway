package com.bmsedge.gateway.filter;

import com.bmsedge.gateway.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Global filter for JWT authentication in API Gateway.
 * Validates JWT tokens and adds user information to request headers.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * List of API endpoints that don't require JWT authentication.
     * These endpoints are accessible without a valid JWT token.
     */
    private static final List<String> OPEN_API_ENDPOINTS = Arrays.asList(
            "/api/auth/signin",
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/register",
            "/api/users/auth/signin",
            "/api/users/auth/signup",
            "/api/users/auth/login",
            "/api/users/auth/register",
            "/signin",
            "/signup",
            "/login",
            "/register",
            "/eureka",
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().toString();

        log.info("JWT Filter - Processing: {} {}", method, path);

        // Skip authentication for open endpoints
        if (isOpenApiEndpoint(path)) {
            log.info("JWT Filter - Path {} is OPEN endpoint, allowing access", path);
            return chain.filter(exchange);
        }

        // Check for Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("JWT Filter - Missing or invalid Authorization header for path: {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        log.debug("JWT Filter - Token found, validating...");

        try {
            if (jwtUtil.validateToken(token)) {
                // Add user info to headers for downstream services
                String username = jwtUtil.extractUsername(token);
                String role = jwtUtil.extractRole(token);

                log.info("JWT Filter - Token valid for user: {}, role: {}", username, role);

                ServerHttpRequest modifiedRequest = request.mutate()
                        .header("X-User-Id", username)
                        .header("X-User-Role", role)
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            } else {
                log.warn("JWT Filter - Invalid or expired token for path: {}", path);
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            }
        } catch (Exception e) {
            log.error("JWT Filter - Token validation failed for path: {} - Error: {}", path, e.getMessage(), e);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Token validation failed: " + e.getMessage());
        }
    }

    /**
     * Checks if the request path matches any open API endpoint.
     * Uses both exact match and startsWith for flexibility.
     */
    private boolean isOpenApiEndpoint(String path) {
        // Check exact match first
        if (OPEN_API_ENDPOINTS.contains(path)) {
            log.debug("JWT Filter - Exact match found for path: {}", path);
            return true;
        }

        // Check if path starts with any open endpoint pattern
        for (String pattern : OPEN_API_ENDPOINTS) {
            if (path.startsWith(pattern)) {
                log.debug("JWT Filter - Pattern match found: {} starts with {}", path, pattern);
                return true;
            }
        }

        log.debug("JWT Filter - No match found for path: {}", path);
        return false;
    }

    /**
     * Returns an error response with the given status.
     */
    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        return response.setComplete();
    }

    /**
     * Returns an error response with the given status and error message.
     */
    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("X-Error-Message", message);
        log.warn("JWT Filter - Returning error: {} - {}", status, message);
        return response.setComplete();
    }

    /**
     * Sets the filter order to execute early in the filter chain.
     * Lower values have higher priority.
     */
    @Override
    public int getOrder() {
        return -1; // High priority - execute early
    }
}