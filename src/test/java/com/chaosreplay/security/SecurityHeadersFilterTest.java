package com.chaosreplay.security;

import com.chaosreplay.api.HealthController;
import com.chaosreplay.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import({SecurityHeadersFilter.class, GlobalExceptionHandler.class})
class SecurityHeadersFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("API responses include OWASP security headers and no-cache directives")
    void apiResponse_includesSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'self'"))
                .andExpect(header().string("Permissions-Policy", "geolocation=(), camera=(), microphone=()"))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"))
                .andExpect(header().string("X-Correlation-ID", matchesPattern("^[a-zA-Z0-9-]{36}$")));
    }

    @Test
    @DisplayName("HTTPS requests emit Strict-Transport-Security header")
    void httpsRequest_includesStrictTransportSecurity() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .secure(true)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000; includeSubDomains"));
    }

    @Test
    @DisplayName("Requests behind TLS-terminating proxy forwarding X-Forwarded-Proto https emit HSTS")
    void forwardedHttpsRequest_includesStrictTransportSecurity() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .header("X-Forwarded-Proto", "https")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000; includeSubDomains"));
    }

    @Test
    @DisplayName("Inbound X-Correlation-ID is preserved and echoed in response")
    void inboundCorrelationId_isPreserved() throws Exception {
        String testCorrelationId = "test-corr-id-998877";

        mockMvc.perform(get("/api/v1/health")
                        .header("X-Correlation-ID", testCorrelationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", testCorrelationId));
    }

    @Test
    @DisplayName("Inbound X-Request-ID is used if X-Correlation-ID is absent")
    void inboundRequestId_isUsedWhenCorrelationIdAbsent() throws Exception {
        String testRequestId = "req-id-12345";

        mockMvc.perform(get("/api/v1/health")
                        .header("X-Request-ID", testRequestId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", testRequestId));
    }
}

