package com.chaosreplay.service;

import com.chaosreplay.api.dto.ReplayScenarioResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.ReplayScenario;
import com.chaosreplay.domain.ReplayScenarioEvent;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.TelemetryEvent;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.ReplayScenarioEventRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import com.chaosreplay.repository.TelemetryEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayScenarioServiceTest {

    @Mock
    private TelemetryEventRepository telemetryEventRepository;

    @Mock
    private ReplayScenarioRepository replayScenarioRepository;

    @Mock
    private ReplayScenarioEventRepository replayScenarioEventRepository;

    @InjectMocks
    private ReplayScenarioService scenarioService;

    @Test
    @DisplayName("createScenario generates deterministic scenario preserving relative offsets, order, and metadata")
    void createScenario_validTrace_generatesScenarioWithCorrectOffsetsAndMetrics() {
        String traceId = "trace-test-100";
        Instant t0 = Instant.parse("2026-09-23T10:00:00.000Z");
        Instant t1 = Instant.parse("2026-09-23T10:00:00.600Z");
        Instant t2 = Instant.parse("2026-09-23T10:00:02.100Z");
        Instant t3 = Instant.parse("2026-09-23T10:00:02.800Z");

        TelemetryEvent e1 = new TelemetryEvent(
                "evt-1", t0, "gateway-service", "gw-01", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "POST /checkout", "Checkout initiated", Map.of()
        );
        TelemetryEvent e2 = new TelemetryEvent(
                "evt-2", t1, "order-service", "ord-01", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "POST /orders", "Order created", Map.of("orderId", "o-999")
        );
        TelemetryEvent e3 = new TelemetryEvent(
                "evt-3", t2, "payment-service", "pay-01", EventType.EXTERNAL_CALL, Severity.ERROR,
                traceId, "req-1", "POST /charges", "Provider timeout", Map.of("provider", "stripe", "timeoutMs", 5000)
        );
        TelemetryEvent e4 = new TelemetryEvent(
                "evt-4", t3, "order-service", "ord-01", EventType.ERROR, Severity.FATAL,
                traceId, "req-1", "POST /orders", "Transaction aborted", Map.of()
        );

        List<TelemetryEvent> telemetryEvents = List.of(e1, e2, e3, e4);

        when(replayScenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtAsc(traceId))
                .thenReturn(Optional.empty());
        when(telemetryEventRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId))
                .thenReturn(telemetryEvents);
        when(replayScenarioRepository.findByScenarioId(any())).thenReturn(Optional.empty());

        when(replayScenarioRepository.saveAndFlush(any(ReplayScenario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(replayScenarioEventRepository.saveAllAndFlush(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReplayScenarioService.ReplayScenarioCreationResult result = scenarioService.createScenario(traceId);

        assertThat(result.newlyCreated()).isTrue();
        ReplayScenarioResponse response = result.scenario();
        assertThat(response).isNotNull();
        assertThat(response.sourceTraceId()).isEqualTo(traceId);
        assertThat(response.eventCount()).isEqualTo(4);
        assertThat(response.failureCount()).isEqualTo(2); // e3 (ERROR) and e4 (FATAL)
        assertThat(response.durationMs()).isEqualTo(2800L); // 10:00:00.000 to 10:00:02.800
        assertThat(response.status()).isEqualTo(ReplayScenarioStatus.CREATED);
        assertThat(response.scenarioId()).startsWith("scen-");

        // Verify scenario events relative offsets
        assertThat(response.events()).hasSize(4);
        assertThat(response.events().get(0).sequenceNumber()).isEqualTo(1);
        assertThat(response.events().get(0).offsetMs()).isEqualTo(0L);
        assertThat(response.events().get(0).eventId()).isEqualTo("evt-1");

        assertThat(response.events().get(1).sequenceNumber()).isEqualTo(2);
        assertThat(response.events().get(1).offsetMs()).isEqualTo(600L);
        assertThat(response.events().get(1).metadata()).containsEntry("orderId", "o-999");

        assertThat(response.events().get(2).sequenceNumber()).isEqualTo(3);
        assertThat(response.events().get(2).offsetMs()).isEqualTo(2100L);
        assertThat(response.events().get(2).metadata())
                .containsEntry("provider", "stripe")
                .containsEntry("timeoutMs", 5000);

        assertThat(response.events().get(3).sequenceNumber()).isEqualTo(4);
        assertThat(response.events().get(3).offsetMs()).isEqualTo(2800L);

        // Verify immutable source telemetry was queried with deterministic ordering
        verify(telemetryEventRepository).findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId);
    }

    @Test
    @DisplayName("createScenario preserves eventId tie-breaker when occurrence timestamps are identical")
    void createScenario_identicalTimestamps_preservesEventIdTieBreaker() {
        String traceId = "trace-tie-breaker";
        Instant t0 = Instant.parse("2026-09-23T11:00:00.000Z");

        TelemetryEvent eA = new TelemetryEvent("evt-a", t0, "srv", null, EventType.LOG, Severity.INFO, traceId, null, null, null, Map.of());
        TelemetryEvent eB = new TelemetryEvent("evt-b", t0, "srv", null, EventType.LOG, Severity.INFO, traceId, null, null, null, Map.of());

        when(replayScenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtAsc(traceId)).thenReturn(Optional.empty());
        when(telemetryEventRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(List.of(eA, eB));
        when(replayScenarioRepository.findByScenarioId(any())).thenReturn(Optional.empty());
        when(replayScenarioRepository.saveAndFlush(any(ReplayScenario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(replayScenarioEventRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ReplayScenarioService.ReplayScenarioCreationResult result = scenarioService.createScenario(traceId);

        assertThat(result.scenario().events()).extracting("eventId").containsExactly("evt-a", "evt-b");
        assertThat(result.scenario().events().get(0).offsetMs()).isZero();
        assertThat(result.scenario().events().get(1).offsetMs()).isZero();
    }

    @Test
    @DisplayName("createScenario is idempotent: returns existing scenario when already created for trace")
    void createScenario_alreadyExists_returnsExistingScenarioWithoutSaving() {
        String traceId = "trace-existing";
        ReplayScenario existingScenario = new ReplayScenario(
                "scen-existing-123", traceId, 2, 0, 500L, ReplayScenarioStatus.CREATED
        );
        ReplayScenarioEvent ev1 = new ReplayScenarioEvent(
                "scen-existing-123", "evt-1", 0L, 1, "srv", "i-1", EventType.REQUEST, Severity.INFO,
                traceId, null, null, null, Map.of()
        );

        when(replayScenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtAsc(traceId))
                .thenReturn(Optional.of(existingScenario));
        when(replayScenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc("scen-existing-123"))
                .thenReturn(List.of(ev1));

        ReplayScenarioService.ReplayScenarioCreationResult result = scenarioService.createScenario(traceId);

        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.scenario().scenarioId()).isEqualTo("scen-existing-123");
        assertThat(result.scenario().sourceTraceId()).isEqualTo(traceId);

        // Verify telemetry was not re-queried and nothing was persisted
        verify(telemetryEventRepository, never()).findAllByTraceIdOrderByTimestampAscEventIdAsc(any());
        verify(replayScenarioRepository, never()).saveAndFlush(any());
        verify(replayScenarioEventRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("createScenario throws ResourceNotFoundException when source trace has no telemetry")
    void createScenario_unknownTrace_throwsResourceNotFoundException() {
        String traceId = "non-existent-trace";
        when(replayScenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtAsc(traceId)).thenReturn(Optional.empty());
        when(telemetryEventRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> scenarioService.createScenario(traceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Trace not found: non-existent-trace");

        verify(replayScenarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("createScenario single event matching both EventType.ERROR and Severity.ERROR counted once")
    void createScenario_dualErrorCriteria_countedOnlyOnce() {
        String traceId = "trace-dual-err";
        Instant t0 = Instant.parse("2026-09-23T12:00:00Z");

        TelemetryEvent dualErr = new TelemetryEvent(
                "evt-dual", t0, "srv", null, EventType.ERROR, Severity.ERROR,
                traceId, null, null, null, Map.of()
        );

        when(replayScenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtAsc(traceId)).thenReturn(Optional.empty());
        when(telemetryEventRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(List.of(dualErr));
        when(replayScenarioRepository.findByScenarioId(any())).thenReturn(Optional.empty());
        when(replayScenarioRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(replayScenarioEventRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ReplayScenarioService.ReplayScenarioCreationResult result = scenarioService.createScenario(traceId);

        assertThat(result.scenario().failureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getScenario returns existing scenario with events ordered by sequence number")
    void getScenario_existingScenario_returnsScenarioWithOrderedEvents() {
        String scenarioId = "scen-valid-1";
        ReplayScenario scenario = new ReplayScenario(scenarioId, "trace-1", 1, 0, 0L, ReplayScenarioStatus.COMPLETED);
        ReplayScenarioEvent event = new ReplayScenarioEvent(
                scenarioId, "evt-1", 0L, 1, "service-a", null, EventType.REQUEST, Severity.INFO,
                "trace-1", null, null, null, Map.of("key", "val")
        );

        when(replayScenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(replayScenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(List.of(event));

        ReplayScenarioResponse response = scenarioService.getScenario(scenarioId);

        assertThat(response).isNotNull();
        assertThat(response.scenarioId()).isEqualTo(scenarioId);
        assertThat(response.status()).isEqualTo(ReplayScenarioStatus.COMPLETED);
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).metadata()).containsEntry("key", "val");
    }

    @Test
    @DisplayName("getScenario throws ResourceNotFoundException when scenarioId does not exist")
    void getScenario_unknownScenario_throwsResourceNotFoundException() {
        String scenarioId = "scen-missing";
        when(replayScenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scenarioService.getScenario(scenarioId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Scenario not found: scen-missing");
    }

    @Test
    @DisplayName("getScenariosForTrace returns all scenarios for the specified traceId")
    void getScenariosForTrace_returnsScenarioList() {
        String traceId = "trace-multi";
        ReplayScenario s1 = new ReplayScenario("scen-1", traceId, 1, 0, 0L, ReplayScenarioStatus.CREATED);

        when(replayScenarioRepository.findBySourceTraceIdOrderByCreatedAtAsc(traceId)).thenReturn(List.of(s1));
        when(replayScenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc("scen-1")).thenReturn(Collections.emptyList());

        List<ReplayScenarioResponse> responses = scenarioService.getScenariosForTrace(traceId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).scenarioId()).isEqualTo("scen-1");
    }
}

