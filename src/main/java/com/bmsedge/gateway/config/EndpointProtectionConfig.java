package com.bmsedge.gateway.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Centralized configuration for API endpoint protection.
 *
 * Add new public endpoints to OPEN_ENDPOINTS.
 * All other endpoints are automatically PROTECTED (require JWT).
 */
public class EndpointProtectionConfig {

    /**
     * Public endpoints that DON'T require JWT authentication.
     *
     * NOTE: These are the ORIGINAL paths (what clients send),
     * not the transformed paths after RewritePath/StripPrefix filters.
     */
    private static final Set<String> OPEN_ENDPOINTS = new HashSet<>(Arrays.asList(
            // ============================================
            // AUTHENTICATION (PUBLIC)
            // ============================================
            "/api/auth/signin",
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/verify",
            "/api/users/auth/signin",
            "/api/users/auth/signup",
            "/api/users/auth/login",
            "/api/users/auth/register",

            // ============================================
            // INFRASTRUCTURE (PUBLIC)
            // ============================================
            "/actuator/health",
            "/actuator/info",
            "/eureka",

            // ============================================
            // TEMPLATES - Made public for easy access
            // ============================================
            "/api/inventory/templates/items",
            "/api/inventory/templates/consumption",
            "/api/inventory/templates/info",

            // ============================================
            // DOCUMENTATION - Made public
            // ============================================
            "/api/inventory/upload/consumption/instructions",
            "/api/inventory/upload/items/instructions",

            // Swagger/API Docs (if using)
            "/swagger-ui",
            "/api-docs",
            "/v3/api-docs"
    ));

    /**
     * Patterns for public endpoints (prefix matching)
     */
    private static final Set<String> OPEN_PATTERNS = new HashSet<>(Arrays.asList(
            // Authentication
            "/api/auth",
            "/api/users/auth",

            // Infrastructure
            "/actuator",
            "/eureka",

            // Templates
            "/api/inventory/templates",

            // ============================================
            // TEMPORARY: Make these public for testing
            // TODO: Remove these after testing, make protected
            // ============================================
            "/api/inventory/items",
            "/api/inventory/categories",
            "/api/inventory/analytics",
            "/api/inventory/upload",
            "/api/inventory/footfall",
            "/api/inventory/statistics"
    ));

    /**
     * Check if a path is a public endpoint (doesn't require authentication)
     */
    public static boolean isPublicEndpoint(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        // Exact match
        if (OPEN_ENDPOINTS.contains(path)) {
            return true;
        }

        // Pattern match (prefix)
        for (String pattern : OPEN_PATTERNS) {
            if (path.startsWith(pattern)) {
                return true;
            }
        }

        // Special case: health endpoints anywhere
        if (path.endsWith("/health") || path.contains("/health/")) {
            return true;
        }

        // Default: protected
        return false;
    }

    /**
     * Check if a path is protected (requires authentication)
     */
    public static boolean isProtectedEndpoint(String path) {
        return !isPublicEndpoint(path);
    }

    /**
     * Get all public endpoint patterns
     */
    public static Set<String> getPublicEndpoints() {
        return new HashSet<>(OPEN_ENDPOINTS);
    }

    /**
     * Get all public endpoint patterns
     */
    public static Set<String> getPublicPatterns() {
        return new HashSet<>(OPEN_PATTERNS);
    }

    /**
     * Add a public endpoint at runtime (for testing/debugging)
     */
    public static void addPublicEndpoint(String endpoint) {
        OPEN_ENDPOINTS.add(endpoint);
    }

    /**
     * Add a public pattern at runtime (for testing/debugging)
     */
    public static void addPublicPattern(String pattern) {
        OPEN_PATTERNS.add(pattern);
    }
}