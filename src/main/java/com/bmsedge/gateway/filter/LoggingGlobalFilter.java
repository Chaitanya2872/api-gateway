package com.bmsedge.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Global filter for logging all requests and responses passing through the gateway.
 * Tracks request/response times, status codes, and key metadata.
 */
@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingGlobalFilter.class);
    private static final String START_TIME_ATTRIBUTE = "startTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Record start time
        exchange.getAttributes().put(START_TIME_ATTRIBUTE, Instant.now());

        // Log incoming request
        logRequest(request);

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    // Log response after completion
                    logResponse(exchange);
                }))
                .doOnError(error -> {
                    // Log errors
                    logError(exchange, error);
                }).then();
    }

    private void logRequest(ServerHttpRequest request) {
        log.info("Incoming Request: {} {} from {}",
                request.getMethod(),
                request.getURI().getPath(),
                request.getRemoteAddress());

        // Log headers (be careful with sensitive data)
        HttpHeaders headers = request.getHeaders();
        log.debug("Request Headers: User-Agent={}, Content-Type={}",
                headers.getFirst(HttpHeaders.USER_AGENT),
                headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }

    private void logResponse(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        Instant startTime = exchange.getAttribute(START_TIME_ATTRIBUTE);
        long duration = startTime != null ?
                Duration.between(startTime, Instant.now()).toMillis() : 0;

        log.info("Outgoing Response: {} {} -> Status: {} | Duration: {}ms",
                request.getMethod(),
                request.getURI().getPath(),
                response.getStatusCode(),
                duration);
    }

    private void logError(ServerWebExchange exchange, Throwable error) {
        ServerHttpRequest request = exchange.getRequest();

        Instant startTime = exchange.getAttribute(START_TIME_ATTRIBUTE);
        long duration = startTime != null ?
                Duration.between(startTime, Instant.now()).toMillis() : 0;

        log.error("Request Error: {} {} | Duration: {}ms | Error: {}",
                request.getMethod(),
                request.getURI().getPath(),
                duration,
                error.getMessage(),
                error);
    }

    @Override
    public int getOrder() {
        return -2; // Execute before JWT filter
    }
}