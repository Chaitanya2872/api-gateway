package com.bmsedge.gateway.filter;

import com.bmsedge.gateway.util.JwtUtil;
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

import java.util.List;

/**
 * Global filter for JWT authentication in API Gateway.
 * Validates JWT tokens and adds user information to request headers.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * List of API endpoints that don't require JWT authentication.
     * These endpoints are accessible without a valid JWT token.
     */
    private static final List<String> OPEN_API_ENDPOINTS = List.of(
            "/api/users/auth/login",      // Login endpoint
            "/api/users/auth/register",   // Registration endpoint
            "/api/auth/",                 // Generic auth endpoints
            "/eureka",                    // Eureka endpoints
            "/actuator"                   // Actuator/health endpoints
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip authentication for open endpoints
        if (isOpenApiEndpoint(path)) {
            return chain.filter(exchange);
        }

        // Check for Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            if (jwtUtil.validateToken(token)) {
                // Add user info to headers for downstream services
                String username = jwtUtil.extractUsername(token);
                String role = jwtUtil.extractRole(token);

                ServerHttpRequest modifiedRequest = request.mutate()
                        .header("X-User-Id", username)
                        .header("X-User-Role", role)
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            } else {
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            }
        } catch (Exception e) {
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Token validation failed: " + e.getMessage());
        }
    }

    /**
     * Checks if the request path matches any open API endpoint.
     */
    private boolean isOpenApiEndpoint(String path) {
        return OPEN_API_ENDPOINTS.stream().anyMatch(path::startsWith);
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