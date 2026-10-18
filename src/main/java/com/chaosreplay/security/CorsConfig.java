package com.chaosreplay.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Applies externalized CORS policies strictly when explicitly enabled.
 * Rejects insecure combinations such as wildcard origin with credentials.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    private final CorsProperties properties;

    public CorsConfig(CorsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!properties.isEnabled()) {
            log.debug("CORS is disabled by default for ChaosReplay");
            return;
        }

        if (properties.getAllowedOrigins().isEmpty()) {
            log.warn("CORS is enabled but allowedOrigins list is empty; no cross-origin requests will be permitted");
            return;
        }

        boolean hasWildcard = properties.getAllowedOrigins().contains("*");
        if (hasWildcard && properties.isAllowCredentials()) {
            throw new IllegalStateException("Insecure CORS configuration: cannot combine wildcard origin '*' with allowCredentials=true");
        }

        log.info("Registering CORS mapping for /api/** with origins={}, methods={}",
                properties.getAllowedOrigins(), properties.getAllowedMethods());

        registry.addMapping("/api/**")
                .allowedOrigins(properties.getAllowedOrigins().toArray(new String[0]))
                .allowedMethods(properties.getAllowedMethods().toArray(new String[0]))
                .allowedHeaders(properties.getAllowedHeaders().toArray(new String[0]))
                .allowCredentials(properties.isAllowCredentials())
                .maxAge(properties.getMaxAge());
    }
}
