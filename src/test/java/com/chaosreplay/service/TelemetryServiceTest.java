package com.chaosreplay.service;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.api.dto.PagedResponse;
import com.chaosreplay.api.dto.TelemetryEventResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.TelemetryEvent;
import com.chaosreplay.exception.DuplicateEventException;
import com.chaosreplay.repository.TelemetryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock
    private TelemetryEventRepository repository;

    @InjectMocks
    private TelemetryService telemetryService;

    private CreateTelemetryEventRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleRequest = new CreateTelemetryEventRequest(
                "evt-100",
                Instant.parse("2026-09-21T15:00:00Z"),
                "order-service",
                "order-instance-1",
                EventType.ERROR,
                Severity.ERROR,
                "trace-xyz",
                "req-abc",
                "POST /orders",
                "Connection timeout to payment gateway",
                Map.of("gateway", "stripe", "timeoutMs", 5000)
        );
    }

    @Test
    @DisplayName("ingestEvent saves entity and returns response when eventId is unique")
    void ingestEvent_success_returnsResponse() {
        when(repository.existsByEventId("evt-100")).thenReturn(false);

        TelemetryEvent mockSaved = new TelemetryEvent(
                sampleRequest.eventId(),
                sampleRequest.timestamp(),
                sampleRequest.serviceName(),
                sampleRequest.serviceInstance(),
                sampleRequest.eventType(),
                sampleRequest.severity(),
                sampleRequest.traceId(),
                sampleRequest.requestId(),
                sampleRequest.operation(),
                sampleRequest.message(),
                sampleRequest.metadata()
        );

        when(repository.saveAndFlush(any(TelemetryEvent.class))).thenReturn(mockSaved);

        TelemetryEventResponse response = telemetryService.ingestEvent(sampleRequest);

        assertThat(response).isNotNull();
        assertThat(response.eventId()).isEqualTo("evt-100");
        assertThat(response.serviceName()).isEqualTo("order-service");
        assertThat(response.eventType()).isEqualTo(EventType.ERROR);
        assertThat(response.severity()).isEqualTo(Severity.ERROR);
        assertThat(response.metadata()).containsEntry("gateway", "stripe");

        ArgumentCaptor<TelemetryEvent> captor = ArgumentCaptor.forClass(TelemetryEvent.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("evt-100");
    }

    @Test
    @DisplayName("ingestEvent throws DuplicateEventException when application check finds existing eventId")
    void ingestEvent_existingEventId_throwsDuplicateEventException() {
        when(repository.existsByEventId("evt-100")).thenReturn(true);

        assertThatThrownBy(() -> telemetryService.ingestEvent(sampleRequest))
                .isInstanceOf(DuplicateEventException.class)
                .hasMessageContaining("evt-100");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("ingestEvent throws DuplicateEventException when race condition triggers database constraint violation")
    void ingestEvent_concurrencyDataIntegrityViolation_throwsDuplicateEventException() {
        when(repository.existsByEventId("evt-100")).thenReturn(false);
        when(repository.saveAndFlush(any(TelemetryEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint uq_telemetry_events_event_id"));

        assertThatThrownBy(() -> telemetryService.ingestEvent(sampleRequest))
                .isInstanceOf(DuplicateEventException.class)
                .hasMessageContaining("evt-100");
    }

    @Test
    @DisplayName("queryEvents returns paginated results matching specification")
    void queryEvents_success_returnsPagedResponse() {
        TelemetryQueryFilter filter = new TelemetryQueryFilter(
                "order-service",
                EventType.ERROR,
                Severity.ERROR,
                "trace-xyz",
                "req-abc",
                Instant.parse("2026-09-21T00:00:00Z"),
                Instant.parse("2026-09-21T23:59:59Z")
        );

        Pageable pageable = PageRequest.of(0, 10);
        TelemetryEvent event = new TelemetryEvent(
                "evt-100",
                Instant.parse("2026-09-21T15:00:00Z"),
                "order-service",
                "order-instance-1",
                EventType.ERROR,
                Severity.ERROR,
                "trace-xyz",
                "req-abc",
                "POST /orders",
                "Timeout",
                Map.of()
        );
        Page<TelemetryEvent> page = new PageImpl<>(List.of(event), pageable, 1);

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PagedResponse<TelemetryEventResponse> result = telemetryService.queryEvents(filter, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
        assertThat(result.content().get(0).eventId()).isEqualTo("evt-100");
    }

    @Test
    @DisplayName("queryEvents returns empty PagedResponse when no events match filter")
    void queryEvents_emptyResult_returnsEmptyPagedResponse() {
        TelemetryQueryFilter filter = new TelemetryQueryFilter(
                "non-existent-service", null, null, null, null, null, null
        );
        Pageable pageable = PageRequest.of(0, 20);
        Page<TelemetryEvent> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

        PagedResponse<TelemetryEventResponse> result = telemetryService.queryEvents(filter, pageable);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    @DisplayName("queryEvents throws IllegalArgumentException when 'from' timestamp is after 'to' timestamp")
    void queryEvents_invalidTimeWindow_throwsIllegalArgumentException() {
        TelemetryQueryFilter filter = new TelemetryQueryFilter(
                "order-service",
                null,
                null,
                null,
                null,
                Instant.parse("2026-09-21T20:00:00Z"),
                Instant.parse("2026-09-21T10:00:00Z")
        );
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> telemetryService.queryEvents(filter, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be after the 'to' timestamp");

        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }
}

