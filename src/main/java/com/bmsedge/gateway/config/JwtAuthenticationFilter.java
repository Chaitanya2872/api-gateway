package com.bmsedge.gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Skip authentication for public endpoints
            if (isPublicEndpoint(request.getPath().value())) {
                return chain.filter(exchange);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return handleUnauthorized(exchange);
            }

            String token = authHeader.substring(7);

            try {
                if (jwtUtil.validateToken(token)) {
                    String username = jwtUtil.getUsernameFromToken(token);
                    // Add user info to headers for downstream services
                    ServerHttpRequest modifiedRequest = request.mutate()
                            .header("X-User-Name", username)
                            .build();

                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                } else {
                    return handleUnauthorized(exchange);
                }
            } catch (Exception e) {
                return handleUnauthorized(exchange);
            }
        };
    }

    private boolean isPublicEndpoint(String path) {
        List<String> publicEndpoints = Arrays.asList(
                "/api/auth/login",
                "/api/auth/register",
                "/api/health",
                "/actuator"
        );

        return publicEndpoints.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    public static class Config {
        // Configuration properties if needed
    }
}

// JWT Utility Class
@Component
class JwtUtil {

    private final String secret = "bms-edge-user-service-secret-key-2024";

    public boolean validateToken(String token) {
        try {
            // Add your JWT validation logic here
            // This is a simplified version
            return token != null && !token.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        // Extract username from JWT token
        // This is a simplified version
        return "user"; // Replace with actual JWT parsing
    }
}