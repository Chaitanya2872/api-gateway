package com.bmsedge.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Rate limiting configuration using Redis.
 * Protects services from abuse and ensures fair usage across tenants.
 */
@Configuration
public class RateLimitConfig {

    /**
     * Rate limit by tenant ID for SAAS fairness (DEFAULT/PRIMARY).
     * Each tenant gets their own rate limit bucket.
     * This is marked as @Primary so Spring Gateway uses it by default.
     */
    @Bean
    @Primary
    public KeyResolver tenantKeyResolver() {
        return exchange -> {
            String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
            return Mono.just(tenantId != null ? tenantId : "unknown");
        };
    }

    /**
     * Rate limit by user ID for individual user protection.
     * Use by specifying key-resolver: "#{@userKeyResolver}" in route config.
     */
    @Bean(name = "userKeyResolver")
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            return Mono.just(userId != null ? userId : "anonymous");
        };
    }

    /**
     * Rate limit by IP address for anonymous/public endpoints.
     * Use by specifying key-resolver: "#{@ipKeyResolver}" in route config.
     */
    @Bean(name = "ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String remoteAddr = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(remoteAddr);
        };
    }

    /**
     * Combined rate limit by tenant + user for granular control.
     * Use by specifying key-resolver: "#{@compositeKeyResolver}" in route config.
     */
    @Bean(name = "compositeKeyResolver")
    public KeyResolver compositeKeyResolver() {
        return exchange -> {
            String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

            String key = (tenantId != null ? tenantId : "unknown") + ":" +
                    (userId != null ? userId : "anonymous");

            return Mono.just(key);
        };
    }
}