package com.chaosreplay.service;

import com.chaosreplay.api.dto.ReplayExecutionResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.ReplayScenario;
import com.chaosreplay.domain.ReplayScenarioEvent;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.ReplayScenarioEventRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayExecutionServiceTest {

    @Mock
    private ReplayScenarioRepository scenarioRepository;

    @Mock
    private ReplayScenarioEventRepository scenarioEventRepository;

    @InjectMocks
    private ReplayExecutionService executionService;

    @Test
    @DisplayName("executeScenario transitions CREATED scenario through RUNNING to COMPLETED and counts simulated failures")
    void executeScenario_validCreatedScenario_executesSimulationAndCompletes() {
        String scenarioId = "scen-sim-001";
        ReplayScenario scenario = new ReplayScenario(
                scenarioId, "trace-101", 3, 1, 1500L, ReplayScenarioStatus.CREATED
        );

        ReplayScenarioEvent ev1 = new ReplayScenarioEvent(
                scenarioId, "evt-1", 0L, 1, "gateway", "gw-1",
                EventType.REQUEST, Severity.INFO, "trace-101", "req-1", "POST /order", "Start", Map.of()
        );
        ReplayScenarioEvent ev2 = new ReplayScenarioEvent(
                scenarioId, "evt-2", 800L, 2, "payment", "pay-1",
                EventType.EXTERNAL_CALL, Severity.ERROR, "trace-101", "req-1", "POST /charge", "Timeout", Map.of("provider", "stripe")
        );
        ReplayScenarioEvent ev3 = new ReplayScenarioEvent(
                scenarioId, "evt-3", 1500L, 3, "order", "ord-1",
                EventType.RESPONSE, Severity.INFO, "trace-101", "req-1", "POST /order", "Aborted", Map.of()
        );

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId))
                .thenReturn(List.of(ev1, ev2, ev3));

        ReplayExecutionResponse response = executionService.executeScenario(scenarioId);

        assertThat(response).isNotNull();
        assertThat(response.scenarioId()).isEqualTo(scenarioId);
        assertThat(response.status()).isEqualTo(ReplayScenarioStatus.COMPLETED);
        assertThat(response.eventsProcessed()).isEqualTo(3);
        assertThat(response.failuresSimulated()).isEqualTo(1); // ev2 has Severity.ERROR
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.completedAt()).isNotNull();
        assertThat(response.durationMs()).isGreaterThanOrEqualTo(0L);

        // Verify status transitions: saved twice (RUNNING then COMPLETED)
        verify(scenarioRepository, times(2)).saveAndFlush(scenario);
        assertThat(scenario.getStatus()).isEqualTo(ReplayScenarioStatus.COMPLETED);
    }

    @Test
    @DisplayName("executeScenario processes all failure types: ERROR, FATAL, and EventType.ERROR")
    void executeScenario_multipleFailureTypes_evaluatesAllSimulatedFailures() {
        String scenarioId = "scen-multi-fail";
        ReplayScenario scenario = new ReplayScenario(scenarioId, "trace-fail", 3, 3, 2000L, ReplayScenarioStatus.CREATED);

        ReplayScenarioEvent e1 = new ReplayScenarioEvent(
                scenarioId, "e-1", 0L, 1, "s1", null, EventType.ERROR, Severity.WARN, "trace-fail", null, null, null, Map.of()
        );
        ReplayScenarioEvent e2 = new ReplayScenarioEvent(
                scenarioId, "e-2", 500L, 2, "s2", null, EventType.LOG, Severity.FATAL, "trace-fail", null, null, null, Map.of()
        );
        ReplayScenarioEvent e3 = new ReplayScenarioEvent(
                scenarioId, "e-3", 1000L, 3, "s3", null, EventType.DATABASE, Severity.ERROR, "trace-fail", null, null, null, Map.of()
        );

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId))
                .thenReturn(List.of(e1, e2, e3));

        ReplayExecutionResponse response = executionService.executeScenario(scenarioId);

        assertThat(response.eventsProcessed()).isEqualTo(3);
        assertThat(response.failuresSimulated()).isEqualTo(3);
    }

    @Test
    @DisplayName("executeScenario throws ResourceNotFoundException when scenarioId is missing")
    void executeScenario_unknownScenario_throwsResourceNotFoundException() {
        String scenarioId = "scen-unknown";
        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executionService.executeScenario(scenarioId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Scenario not found: scen-unknown");

        verify(scenarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("executeScenario throws IllegalStateException when scenario is already RUNNING")
    void executeScenario_alreadyRunning_throwsIllegalStateException() {
        String scenarioId = "scen-running";
        ReplayScenario scenario = new ReplayScenario(
                scenarioId, "trace-1", 2, 0, 100L, ReplayScenarioStatus.RUNNING
        );

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));

        assertThatThrownBy(() -> executionService.executeScenario(scenarioId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currently RUNNING");

        verify(scenarioRepository, never()).saveAndFlush(any());
        verify(scenarioEventRepository, never()).findAllByScenarioIdOrderBySequenceNumberAsc(any());
    }
}
