package com.bmsedge.gateway.filter;

import com.bmsedge.gateway.config.EndpointProtectionConfig;
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

/**
 * Global JWT Authentication Filter for API Gateway
 *
 * Uses EndpointProtectionConfig to determine which endpoints are public.
 * Add new public endpoints to EndpointProtectionConfig, not here.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().toString();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            return chain.filter(exchange);
        }

        log.info("JWT Filter - Processing: {} {}", method, path);

        // Check if endpoint is public
        if (EndpointProtectionConfig.isPublicEndpoint(path)) {
            log.info("JWT Filter - PUBLIC endpoint '{}', allowing access", path);
            return chain.filter(exchange);
        }

        // Protected endpoint - requires authentication
        log.debug("JWT Filter - PROTECTED endpoint '{}', checking authentication", path);

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("JWT Filter - UNAUTHORIZED: No token for {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header. Please login.");
        }

        String token = authHeader.substring(7);

        try {
            if (jwtUtil.validateToken(token)) {
                String username = jwtUtil.extractUsername(token);
                String role = jwtUtil.extractRole(token);

                log.info("JWT Filter - AUTHORIZED: user='{}', role='{}', path='{}'",
                        username, role, path);

                // Add user context to headers
                ServerHttpRequest modifiedRequest = request.mutate()
                        .header("X-User-Id", username)
                        .header("X-User-Role", role)
                        .header("X-User-Email", username)
                        .header("X-Authenticated", "true")
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            } else {
                log.warn("JWT Filter - UNAUTHORIZED: Invalid token for {}", path);
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.error("JWT Filter - UNAUTHORIZED: Expired token for {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Token expired. Please login again.");
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.error("JWT Filter - UNAUTHORIZED: Malformed token for {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Malformed token");
        } catch (io.jsonwebtoken.SignatureException e) {
            log.error("JWT Filter - UNAUTHORIZED: Invalid signature for {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid token signature");
        } catch (Exception e) {
            log.error("JWT Filter - ERROR: {} for {}", e.getMessage(), path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Authentication failed");
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("X-Error-Message", message);
        response.getHeaders().add("Content-Type", "application/json");
        log.warn("JWT Filter - Error response: {} - {}", status, message);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1; // Execute before route transformations
    }
}