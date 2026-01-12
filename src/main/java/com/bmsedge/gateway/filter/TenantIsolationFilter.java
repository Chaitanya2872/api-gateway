package com.bmsedge.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Tenant isolation filter for SAAS multi-tenancy.
 * Extracts tenant ID from request and adds it to downstream service headers.
 * Critical for data isolation in a multi-tenant SAAS platform.
 */
@Component
public class TenantIsolationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantIsolationFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String TENANT_SUBDOMAIN_HEADER = "X-Tenant-Subdomain";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip tenant isolation for authentication endpoints
        if (isAuthEndpoint(path)) {
            return chain.filter(exchange);
        }

        // Extract tenant ID from different sources
        String tenantId = extractTenantId(exchange);
        String tenantSubdomain = extractTenantSubdomain(exchange);

        if (tenantId == null) {
            log.warn("No tenant ID found for request: {}", path);
            return onError(exchange, HttpStatus.BAD_REQUEST, "Missing tenant identifier");
        }

        // Add tenant information to request headers for downstream services
        ServerHttpRequest modifiedRequest = request.mutate()
                .header(TENANT_HEADER, tenantId)
                .header(TENANT_SUBDOMAIN_HEADER, tenantSubdomain != null ? tenantSubdomain : "")
                .build();

        log.debug("Request from tenant: {} | Path: {}", tenantId, path);

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    /**
     * Extract tenant ID from multiple sources (in priority order):
     * 1. JWT token claim (most secure)
     * 2. Custom header (for service-to-service calls)
     * 3. Subdomain (e.g., tenant1.bmsedge.com)
     * 4. Query parameter (least secure, for backward compatibility)
     */
    private String extractTenantId(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. From JWT token (if already processed by JWT filter)
        String tenantFromHeader = request.getHeaders().getFirst("X-User-Tenant-Id");
        if (tenantFromHeader != null && !tenantFromHeader.isBlank()) {
            return tenantFromHeader;
        }

        // 2. From custom header (for API calls)
        String customHeader = request.getHeaders().getFirst(TENANT_HEADER);
        if (customHeader != null && !customHeader.isBlank()) {
            return customHeader;
        }

        // 3. From subdomain
        String subdomain = extractTenantSubdomain(exchange);
        if (subdomain != null && !subdomain.isBlank()) {
            return subdomain;
        }

        // 4. From query parameter (least preferred)
        String queryParam = request.getQueryParams().getFirst("tenantId");
        if (queryParam != null && !queryParam.isBlank()) {
            return queryParam;
        }

        return null;
    }

    /**
     * Extract tenant subdomain from host header.
     * Example: tenant1.bmsedge.com -> tenant1
     */
    private String extractTenantSubdomain(ServerWebExchange exchange) {
        String host = exchange.getRequest().getHeaders().getFirst("Host");

        if (host == null || host.isBlank()) {
            return null;
        }

        // Remove port if present
        host = host.split(":")[0];

        // Extract subdomain (assuming format: subdomain.domain.com)
        String[] parts = host.split("\\.");
        if (parts.length >= 3) {
            // Return subdomain only if it's not 'www' or 'api'
            String subdomain = parts[0];
            if (!subdomain.equalsIgnoreCase("www") &&
                    !subdomain.equalsIgnoreCase("api") &&
                    !subdomain.equalsIgnoreCase("localhost")) {
                return subdomain;
            }
        }

        return null;
    }

    private boolean isAuthEndpoint(String path) {
        return path.startsWith("/api/users/auth/") ||
                path.startsWith("/api/auth/") ||
                path.startsWith("/actuator/");
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("X-Error-Message", message);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return 0; // Execute after JWT filter but before business logic
    }
}