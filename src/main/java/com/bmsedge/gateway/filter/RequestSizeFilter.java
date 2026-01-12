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
 * Request size validation filter to prevent DoS attacks and manage bandwidth.
 * Configured per route in application.yml with RequestSize filter.
 * This global filter provides a safety net for all routes.
 */
@Component
public class RequestSizeFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestSizeFilter.class);

    // Default maximum request size: 10MB
    private static final long DEFAULT_MAX_SIZE = 10 * 1024 * 1024; // 10MB

    // Maximum for file uploads: 50MB
    private static final long MAX_UPLOAD_SIZE = 50 * 1024 * 1024; // 50MB

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Get content length from headers
        long contentLength = request.getHeaders().getContentLength();

        if (contentLength < 0) {
            // No content-length header, allow it to proceed
            // (the underlying system will handle streaming)
            return chain.filter(exchange);
        }

        // Determine max size based on endpoint
        long maxSize = determineMaxSize(path);

        if (contentLength > maxSize) {
            log.warn("Request size {} exceeds maximum {} for path: {}",
                    contentLength, maxSize, path);
            return onError(exchange, HttpStatus.PAYLOAD_TOO_LARGE,
                    "Request size exceeds maximum allowed: " + formatBytes(maxSize));
        }

        log.debug("Request size: {} for path: {}", formatBytes(contentLength), path);

        return chain.filter(exchange);
    }

    /**
     * Determine maximum request size based on the endpoint path
     */
    private long determineMaxSize(String path) {
        // File upload endpoints get higher limit
        if (path.contains("/upload") ||
                path.contains("/import") ||
                path.contains("/bulk")) {
            return MAX_UPLOAD_SIZE;
        }

        // IoT sensor data might have larger payloads
        if (path.contains("/api/sensors/batch")) {
            return 20 * 1024 * 1024; // 20MB
        }

        // Default for all other endpoints
        return DEFAULT_MAX_SIZE;
    }

    /**
     * Format bytes into human-readable string
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), unit);
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("X-Error-Message", message);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -4; // Execute very early, before processing begins
    }
}