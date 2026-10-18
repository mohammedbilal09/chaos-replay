package com.chaosreplay.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Servlet filter that applies OWASP-recommended security headers to all HTTP responses,
 * disables caching for sensitive API endpoints, and manages request correlation IDs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String CORRELATION_ID_ATTR = "chaosreplay.correlationId";
    private static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[a-zA-Z0-9-_.:]{1,64}$");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            // Apply essential OWASP security headers
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-Frame-Options", "DENY");
            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            httpResponse.setHeader("Content-Security-Policy", "default-src 'self'");
            httpResponse.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=()");

            // Emit HSTS strictly when HTTPS is used or guaranteed by a trusted reverse proxy (RFC 6797)
            if (httpRequest.isSecure() || "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto"))) {
                httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }

            // Disallow caching for dynamic API paths
            String uri = httpRequest.getRequestURI();
            if (uri != null && uri.startsWith("/api/")) {
                httpResponse.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
                httpResponse.setHeader("Pragma", "no-cache");
                httpResponse.setHeader("Expires", "0");
            }

            // Extract or establish correlation identifier
            String correlationId = resolveCorrelationId(httpRequest);
            httpRequest.setAttribute(CORRELATION_ID_ATTR, correlationId);
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            MDC.put(MDC_KEY, correlationId);

            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove(MDC_KEY);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(CORRELATION_ID_HEADER);
        if (incoming == null || incoming.isBlank()) {
            incoming = request.getHeader(REQUEST_ID_HEADER);
        }

        if (incoming != null && !incoming.isBlank()) {
            String trimmed = incoming.trim();
            if (SAFE_CORRELATION_ID.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }
}

