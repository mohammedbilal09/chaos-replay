package com.chaosreplay.service;

import com.chaosreplay.api.dto.RequestCorrelationResponse;
import com.chaosreplay.api.dto.TraceCorrelationResponse;
import com.chaosreplay.api.dto.TraceReconstructionResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.TelemetryEvent;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.TelemetryEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceCorrelationServiceTest {

    @Mock
    private TelemetryEventRepository repository;

    @InjectMocks
    private TraceCorrelationService correlationService;

    @Test
    @DisplayName("getTraceCorrelation returns chronologically ordered events and trace-level summary")
    void getTraceCorrelation_traceExists_returnsTraceCorrelationResponse() {
        String traceId = "trace-100";
        Instant t1 = Instant.parse("2026-09-21T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T10:00:01Z");
        Instant t3 = Instant.parse("2026-09-21T10:00:02Z");

        TelemetryEvent event1 = new TelemetryEvent(
                "evt-1", t1, "gateway-service", "gw-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "POST /checkout", "Inbound request", Map.of()
        );
        TelemetryEvent event2 = new TelemetryEvent(
                "evt-2", t2, "order-service", "ord-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "POST /orders", "Order created", Map.of()
        );
        TelemetryEvent event3 = new TelemetryEvent(
                "evt-3", t3, "payment-service", "pay-1", EventType.ERROR, Severity.ERROR,
                traceId, "req-1", "POST /charge", "Gateway timeout", Map.of()
        );

        when(repository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId))
                .thenReturn(List.of(event1, event2, event3));

        TraceCorrelationResponse response = correlationService.getTraceCorrelation(traceId);

        assertThat(response).isNotNull();
        assertThat(response.traceId()).isEqualTo(traceId);
        assertThat(response.eventCount()).isEqualTo(3);
        assertThat(response.hasErrors()).isTrue();
        assertThat(response.highestSeverity()).isEqualTo(Severity.ERROR);
        assertThat(response.services()).containsExactly("gateway-service", "order-service", "payment-service");
        assertThat(response.events()).hasSize(3);
        assertThat(response.events().get(0).eventId()).isEqualTo("evt-1");
        assertThat(response.events().get(1).eventId()).isEqualTo("evt-2");
        assertThat(response.events().get(2).eventId()).isEqualTo("evt-3");

        verify(repository).findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId);
    }

    @Test
    @DisplayName("getTraceCorrelation preserves appearance order for services and ignores duplicates")
    void getTraceCorrelation_duplicateServices_preservesAppearanceOrder() {
        String traceId = "trace-dup-svc";
        Instant t1 = Instant.parse("2026-09-21T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T10:00:01Z");
        Instant t3 = Instant.parse("2026-09-21T10:00:02Z");

        TelemetryEvent e1 = new TelemetryEvent("evt-1", t1, "service-A", null, EventType.LOG, Severity.DEBUG, traceId, null, null, null, Map.of());
        TelemetryEvent e2 = new TelemetryEvent("evt-2", t2, "service-B", null, EventType.LOG, Severity.INFO, traceId, null, null, null, Map.of());
        TelemetryEvent e3 = new TelemetryEvent("evt-3", t3, "service-A", null, EventType.LOG, Severity.WARN, traceId, null, null, null, Map.of());

        when(repository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId))
                .thenReturn(List.of(e1, e2, e3));

        TraceCorrelationResponse response = correlationService.getTraceCorrelation(traceId);

        assertThat(response.services()).containsExactly("service-A", "service-B");
        assertThat(response.highestSeverity()).isEqualTo(Severity.WARN);
        assertThat(response.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("getTraceCorrelation throws ResourceNotFoundException when traceId does not exist")
    void getTraceCorrelation_unknownTrace_throwsResourceNotFoundException() {
        String traceId = "unknown-trace";
        when(repository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> correlationService.getTraceCorrelation(traceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Trace not found: unknown-trace");
    }

    @Test
    @DisplayName("getTraceCorrelation throws ResourceNotFoundException when traceId is null or blank")
    void getTraceCorrelation_blankTraceId_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> correlationService.getTraceCorrelation("   "))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> correlationService.getTraceCorrelation(null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getRequestCorrelation returns chronologically ordered events and request-level summary")
    void getRequestCorrelation_requestExists_returnsRequestCorrelationResponse() {
        String requestId = "req-555";
        Instant t1 = Instant.parse("2026-09-21T12:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T12:00:01Z");

        TelemetryEvent event1 = new TelemetryEvent(
                "evt-r1", t1, "auth-service", "auth-1", EventType.REQUEST, Severity.INFO,
                "trace-1", requestId, "POST /login", "Attempt login", Map.of()
        );
        TelemetryEvent event2 = new TelemetryEvent(
                "evt-r2", t2, "auth-service", "auth-1", EventType.RESPONSE, Severity.INFO,
                "trace-1", requestId, "POST /login", "Login success", Map.of()
        );

        when(repository.findAllByRequestIdOrderByTimestampAscEventIdAsc(requestId))
                .thenReturn(List.of(event1, event2));

        RequestCorrelationResponse response = correlationService.getRequestCorrelation(requestId);

        assertThat(response).isNotNull();
        assertThat(response.requestId()).isEqualTo(requestId);
        assertThat(response.eventCount()).isEqualTo(2);
        assertThat(response.hasErrors()).isFalse();
        assertThat(response.highestSeverity()).isEqualTo(Severity.INFO);
        assertThat(response.services()).containsExactly("auth-service");
        assertThat(response.events()).hasSize(2);

        verify(repository).findAllByRequestIdOrderByTimestampAscEventIdAsc(requestId);
    }

    @Test
    @DisplayName("getRequestCorrelation throws ResourceNotFoundException when requestId does not exist")
    void getRequestCorrelation_unknownRequest_throwsResourceNotFoundException() {
        String requestId = "unknown-req";
        when(repository.findAllByRequestIdOrderByTimestampAscEventIdAsc(requestId)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> correlationService.getRequestCorrelation(requestId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Request not found: unknown-req");
    }

    @Test
    @DisplayName("reconstructTrace calculates duration, timeline, and identifies first failure")
    void reconstructTrace_withFailures_returnsReconstructionWithFirstFailure() {
        String traceId = "trace-recon-1";
        Instant t1 = Instant.parse("2026-09-21T14:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T14:00:01.500Z");
        Instant t3 = Instant.parse("2026-09-21T14:00:02.000Z");
        Instant t4 = Instant.parse("2026-09-21T14:00:03.200Z");

        TelemetryEvent event1 = new TelemetryEvent(
                "evt-1", t1, "ingress-proxy", "p-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "GET /api/checkout", "Ingress started", Map.of()
        );
        TelemetryEvent event2 = new TelemetryEvent(
                "evt-2", t2, "order-service", "o-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "POST /orders", "Processing", Map.of()
        );
        // First failure: ERROR severity
        TelemetryEvent event3 = new TelemetryEvent(
                "evt-3", t3, "payment-service", "pay-1", EventType.EXTERNAL_CALL, Severity.ERROR,
                traceId, "req-1", "POST /charge", "Payment provider connection timeout", Map.of("provider", "stripe")
        );
        // Subsequent failure: FATAL severity
        TelemetryEvent event4 = new TelemetryEvent(
                "evt-4", t4, "order-service", "o-1", EventType.ERROR, Severity.FATAL,
                traceId, "req-1", "POST /orders", "Fatal checkout transaction abort", Map.of()
        );

        when(repository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId))
                .thenReturn(List.of(event1, event2, event3, event4));

        TraceReconstructionResponse response = correlationService.reconstructTrace(traceId);

        assertThat(response).isNotNull();
        assertThat(response.traceId()).isEqualTo(traceId);
        assertThat(response.eventCount()).isEqualTo(4);
        assertThat(response.services()).containsExactly("ingress-proxy", "order-service", "payment-service");
        assertThat(response.startTime()).isEqualTo(t1);
        assertThat(response.endTime()).isEqualTo(t4);
        assertThat(response.durationMs()).isEqualTo(3200L); // from 14:00:00 to 14:00:03.200
        assertThat(response.hasFailure()).isTrue();
        assertThat(response.failureCount()).isEqualTo(2);

        // Verify first failure details correspond specifically to event3
        assertThat(response.firstFailure()).isNotNull();
        assertThat(response.firstFailure().eventId()).isEqualTo("evt-3");
        assertThat(response.firstFailure().serviceName()).isEqualTo("payment-service");
        assertThat(response.firstFailure().serviceInstance()).isEqualTo("pay-1");
        assertThat(response.firstFailure().timestamp()).isEqualTo(t3);
        assertThat(response.firstFailure().eventType()).isEqualTo(EventType.EXTERNAL_CALL);
        assertThat(response.firstFailure().severity()).isEqualTo(Severity.ERROR);
        assertThat(response.firstFailure().operation()).isEqualTo("POST /charge");
        assertThat(response.firstFailure().message()).isEqualTo("Payment provider connection timeout");

        assertThat(response.timeline()).hasSize(4);
        assertThat(response.timeline().get(0).eventId()).isEqualTo("evt-1");
        assertThat(response.timeline().get(3).eventId()).isEqualTo("evt-4");
    }

    @Test
    @DisplayName("reconstructTrace when no failures exist returns hasFailure=false and firstFailure=null")
    void reconstructTrace_noFailures_returnsCleanReconstruction() {
        String traceId = "trace-healthy";
        Instant t1 = Instant.parse("2026-09-21T15:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T15:00:00.850Z");

        TelemetryEvent event1 = new TelemetryEvent(
                "evt-h1", t1, "catalog-service", "cat-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-h", "GET /items", "Fetch items", Map.of()
        );
        TelemetryEvent event2 = new TelemetryEvent(
                "evt-h2", t2, "catalog-service", "cat-1", EventType.RESPONSE, Severity.DEBUG,
                traceId, "req-h", "GET /items", "Items returned", Map.of()
        );

        when(repository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId))
                .thenReturn(List.of(event1, event2));

        TraceReconstructionResponse response = correlationService.reconstructTrace(traceId);

        assertThat(response.hasFailure()).isFalse();
        assertThat(response.failureCount()).isZero();
        assertThat(response.firstFailure()).isNull();
        assertThat(response.durationMs()).isEqualTo(850L);
    }

    @Test
    @DisplayName("reconstructTrace for single-event trace calculates 0ms duration and matching start/end time")
    void reconstructTrace_singleEvent_durationZero() {
        String traceId = "trace-single";
        Instant t1 = Instant.parse("2026-09-21T16:00:00Z");

        TelemetryEvent singleEvent = new TelemetryEvent(
                "evt-s1", t1, "ping-service", "ping-1", EventType.LOG, Severity.INFO,
                traceId, null, "PING", "Health ping", Map.of()
        );

        when(repository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId))
                .thenReturn(List.of(singleEvent));

        TraceReconstructionResponse response = correlationService.reconstructTrace(traceId);

        assertThat(response.eventCount()).isEqualTo(1);
        assertThat(response.startTime()).isEqualTo(t1);
        assertThat(response.endTime()).isEqualTo(t1);
        assertThat(response.durationMs()).isZero();
    }

    @Test
    @DisplayName("reconstructTrace event matching both EventType.ERROR and Severity.ERROR is counted only once")
    void reconstructTrace_bothEventTypeAndSeverityError_countedOnlyOnce() {
        String traceId = "trace-multi-crit";
        Instant t1 = Instant.parse("2026-09-21T17:00:00Z");

        TelemetryEvent dualErrorEvent = new TelemetryEvent(
                "evt-dual", t1, "db-service", "db-1", EventType.ERROR, Severity.ERROR,
                traceId, "req-dual", "COMMIT", "Deadlock detected", Map.of()
        );

        when(repository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId))
                .thenReturn(List.of(dualErrorEvent));

        TraceReconstructionResponse response = correlationService.reconstructTrace(traceId);

        assertThat(response.hasFailure()).isTrue();
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.firstFailure().eventId()).isEqualTo("evt-dual");
    }

    @Test
    @DisplayName("reconstructTrace throws ResourceNotFoundException when traceId does not exist")
    void reconstructTrace_unknownTrace_throwsResourceNotFoundException() {
        String traceId = "missing-trace";
        when(repository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> correlationService.reconstructTrace(traceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Trace not found: missing-trace");
    }
}

