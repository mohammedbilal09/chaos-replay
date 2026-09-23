package com.chaosreplay.service;

import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.ReplayObservedEvent;
import com.chaosreplay.domain.ReplayScenario;
import com.chaosreplay.domain.ReplayScenarioEvent;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.ReplayVerification;
import com.chaosreplay.domain.ReplayVerificationDifference;
import com.chaosreplay.domain.ReplayVerificationStatus;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.VerificationDifferenceType;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.ReplayScenarioEventRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import com.chaosreplay.repository.ReplayVerificationDifferenceRepository;
import com.chaosreplay.repository.ReplayVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayVerificationServiceTest {

    @Mock
    private ReplayScenarioRepository scenarioRepository;

    @Mock
    private ReplayScenarioEventRepository scenarioEventRepository;

    @Mock
    private ReplayVerificationRepository verificationRepository;

    @Mock
    private ReplayVerificationDifferenceRepository differenceRepository;

    private ReplayVerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new ReplayVerificationService(
                scenarioRepository,
                scenarioEventRepository,
                verificationRepository,
                differenceRepository
        );
    }

    private ReplayScenario createSampleScenario(String scenarioId, int failureCount) {
        return new ReplayScenario(
                scenarioId,
                "trace-100",
                3,
                failureCount,
                200L,
                ReplayScenarioStatus.COMPLETED
        );
    }

    private List<ReplayScenarioEvent> createSampleScenarioEvents(String scenarioId) {
        return List.of(
                new ReplayScenarioEvent(scenarioId, "evt-1", 0L, 1, "order-service", "inst-1",
                        EventType.REQUEST, Severity.INFO, "trace-100", "req-1", "createOrder", "received", Map.of()),
                new ReplayScenarioEvent(scenarioId, "evt-2", 80L, 2, "payment-service", "inst-2",
                        EventType.DATABASE, Severity.ERROR, "trace-100", "req-1", "charge", "failed", Map.of()),
                new ReplayScenarioEvent(scenarioId, "evt-3", 200L, 3, "order-service", "inst-1",
                        EventType.EXTERNAL_CALL, Severity.FATAL, "trace-100", "req-1", "abort", "aborted", Map.of())
        );
    }

    @Test
    @DisplayName("1. Perfect replay produces PASSED verification with zero differences")
    void perfectReplay_producesPassed() {
        String scenarioId = "scen-test-01";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = verificationService.verifyScenario(scenarioId);

        assertThat(result.newlyCreated()).isTrue();
        var resp = result.verification();
        assertThat(resp.status()).isEqualTo(ReplayVerificationStatus.PASSED);
        assertThat(resp.eventsMatched()).isEqualTo(3);
        assertThat(resp.eventsMissing()).isZero();
        assertThat(resp.eventsUnexpected()).isZero();
        assertThat(resp.severityMismatches()).isZero();
        assertThat(resp.eventTypeMismatches()).isZero();
        assertThat(resp.serviceMismatches()).isZero();
        assertThat(resp.originalFailureCount()).isEqualTo(2);
        assertThat(resp.replayedFailureCount()).isEqualTo(2);
        assertThat(resp.differences()).isEmpty();
    }

    @Test
    @DisplayName("2. Missing event produces FAILED verification with MISSING_EVENT difference")
    void missingEvent_producesFailed() {
        String scenarioId = "scen-test-02";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(differenceRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Observation is missing sequence 3
        List<ReplayObservedEvent> observations = List.of(
                new ReplayObservedEvent(1, "evt-1", "order-service", EventType.REQUEST, Severity.INFO, 0L),
                new ReplayObservedEvent(2, "evt-2", "payment-service", EventType.DATABASE, Severity.ERROR, 80L)
        );

        var result = verificationService.verifyScenarioWithObservations(scenarioId, observations, 150L);

        var resp = result.verification();
        assertThat(resp.status()).isEqualTo(ReplayVerificationStatus.FAILED);
        assertThat(resp.eventsMissing()).isEqualTo(1);
        assertThat(resp.differences()).hasSize(1);
        assertThat(resp.differences().get(0).differenceType()).isEqualTo(VerificationDifferenceType.MISSING_EVENT);
        assertThat(resp.differences().get(0).sequenceNumber()).isEqualTo(3);
        assertThat(resp.differences().get(0).expectedEventId()).isEqualTo("evt-3");
    }

    @Test
    @DisplayName("3. Unexpected event produces FAILED verification with UNEXPECTED_EVENT difference")
    void unexpectedEvent_producesFailed() {
        String scenarioId = "scen-test-03";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(differenceRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Observation has an unexpected extra sequence 4
        List<ReplayObservedEvent> observations = List.of(
                new ReplayObservedEvent(1, "evt-1", "order-service", EventType.REQUEST, Severity.INFO, 0L),
                new ReplayObservedEvent(2, "evt-2", "payment-service", EventType.DATABASE, Severity.ERROR, 80L),
                new ReplayObservedEvent(3, "evt-3", "order-service", EventType.EXTERNAL_CALL, Severity.FATAL, 200L),
                new ReplayObservedEvent(4, "evt-ghost", "analytics-service", EventType.RESPONSE, Severity.ERROR, 250L)
        );

        var result = verificationService.verifyScenarioWithObservations(scenarioId, observations, 250L);

        var resp = result.verification();
        assertThat(resp.status()).isEqualTo(ReplayVerificationStatus.FAILED);
        assertThat(resp.eventsUnexpected()).isEqualTo(1);
        assertThat(resp.differences()).hasSize(1);
        assertThat(resp.differences().get(0).differenceType()).isEqualTo(VerificationDifferenceType.UNEXPECTED_EVENT);
        assertThat(resp.differences().get(0).actualEventId()).isEqualTo("evt-ghost");
    }

    @Test
    @DisplayName("4. Severity mismatch produces FAILED verification with SEVERITY_MISMATCH")
    void severityMismatch_producesFailed() {
        String scenarioId = "scen-test-04";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(differenceRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Sequence 2 observed with WARN instead of ERROR
        List<ReplayObservedEvent> observations = List.of(
                new ReplayObservedEvent(1, "evt-1", "order-service", EventType.REQUEST, Severity.INFO, 0L),
                new ReplayObservedEvent(2, "evt-2", "payment-service", EventType.DATABASE, Severity.WARN, 80L),
                new ReplayObservedEvent(3, "evt-3", "order-service", EventType.EXTERNAL_CALL, Severity.FATAL, 200L)
        );

        var result = verificationService.verifyScenarioWithObservations(scenarioId, observations, 200L);

        var resp = result.verification();
        assertThat(resp.status()).isEqualTo(ReplayVerificationStatus.FAILED);
        assertThat(resp.severityMismatches()).isEqualTo(1);
        assertThat(resp.differences().get(0).differenceType()).isEqualTo(VerificationDifferenceType.SEVERITY_MISMATCH);
        assertThat(resp.differences().get(0).expectedSeverity()).isEqualTo("ERROR");
        assertThat(resp.differences().get(0).actualSeverity()).isEqualTo("WARN");
    }

    @Test
    @DisplayName("5. Event type mismatch produces FAILED verification with EVENT_TYPE_MISMATCH")
    void eventTypeMismatch_producesFailed() {
        String scenarioId = "scen-test-05";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(differenceRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Sequence 1 observed as RESPONSE instead of REQUEST
        List<ReplayObservedEvent> observations = List.of(
                new ReplayObservedEvent(1, "evt-1", "order-service", EventType.RESPONSE, Severity.INFO, 0L),
                new ReplayObservedEvent(2, "evt-2", "payment-service", EventType.DATABASE, Severity.ERROR, 80L),
                new ReplayObservedEvent(3, "evt-3", "order-service", EventType.EXTERNAL_CALL, Severity.FATAL, 200L)
        );

        var result = verificationService.verifyScenarioWithObservations(scenarioId, observations, 200L);

        var resp = result.verification();
        assertThat(resp.status()).isEqualTo(ReplayVerificationStatus.FAILED);
        assertThat(resp.eventTypeMismatches()).isEqualTo(1);
        assertThat(resp.differences().get(0).differenceType()).isEqualTo(VerificationDifferenceType.EVENT_TYPE_MISMATCH);
        assertThat(resp.differences().get(0).expectedEventType()).isEqualTo("REQUEST");
        assertThat(resp.differences().get(0).actualEventType()).isEqualTo("RESPONSE");
    }

    @Test
    @DisplayName("6. Service mismatch produces FAILED verification with SERVICE_MISMATCH")
    void serviceMismatch_producesFailed() {
        String scenarioId = "scen-test-06";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(differenceRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Sequence 2 observed on checkout-service instead of payment-service
        List<ReplayObservedEvent> observations = List.of(
                new ReplayObservedEvent(1, "evt-1", "order-service", EventType.REQUEST, Severity.INFO, 0L),
                new ReplayObservedEvent(2, "evt-2", "checkout-service", EventType.DATABASE, Severity.ERROR, 80L),
                new ReplayObservedEvent(3, "evt-3", "order-service", EventType.EXTERNAL_CALL, Severity.FATAL, 200L)
        );

        var result = verificationService.verifyScenarioWithObservations(scenarioId, observations, 200L);

        var resp = result.verification();
        assertThat(resp.status()).isEqualTo(ReplayVerificationStatus.FAILED);
        assertThat(resp.serviceMismatches()).isEqualTo(1);
        assertThat(resp.differences().get(0).differenceType()).isEqualTo(VerificationDifferenceType.SERVICE_MISMATCH);
        assertThat(resp.differences().get(0).expectedServiceName()).isEqualTo("payment-service");
        assertThat(resp.differences().get(0).actualServiceName()).isEqualTo("checkout-service");
    }

    @Test
    @DisplayName("7. Failure count mismatch produces FAILED verification")
    void failureCountMismatch_producesFailed() {
        String scenarioId = "scen-test-07";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(differenceRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Sequence 2 observed with INFO (no error), so failure count is 1 instead of 2
        List<ReplayObservedEvent> observations = List.of(
                new ReplayObservedEvent(1, "evt-1", "order-service", EventType.REQUEST, Severity.INFO, 0L),
                new ReplayObservedEvent(2, "evt-2", "payment-service", EventType.DATABASE, Severity.INFO, 80L),
                new ReplayObservedEvent(3, "evt-3", "order-service", EventType.EXTERNAL_CALL, Severity.FATAL, 200L)
        );

        var result = verificationService.verifyScenarioWithObservations(scenarioId, observations, 200L);

        var resp = result.verification();
        assertThat(resp.status()).isEqualTo(ReplayVerificationStatus.FAILED);
        assertThat(resp.originalFailureCount()).isEqualTo(2);
        assertThat(resp.replayedFailureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("8. Partial pass when failure count matches but non-critical severity changes exist")
    void partialPass_producesPartial() {
        String scenarioId = "scen-test-08";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(differenceRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Sequence 1 observed with DEBUG instead of INFO (non-critical severity change: both non-errors)
        // Failure count remains 2 (sequences 2 and 3 remain ERROR and FATAL)
        List<ReplayObservedEvent> observations = List.of(
                new ReplayObservedEvent(1, "evt-1", "order-service", EventType.REQUEST, Severity.DEBUG, 0L),
                new ReplayObservedEvent(2, "evt-2", "payment-service", EventType.DATABASE, Severity.ERROR, 80L),
                new ReplayObservedEvent(3, "evt-3", "order-service", EventType.EXTERNAL_CALL, Severity.FATAL, 200L)
        );

        var result = verificationService.verifyScenarioWithObservations(scenarioId, observations, 200L);

        var resp = result.verification();
        assertThat(resp.status()).isEqualTo(ReplayVerificationStatus.PARTIAL);
        assertThat(resp.originalFailureCount()).isEqualTo(2);
        assertThat(resp.replayedFailureCount()).isEqualTo(2);
        assertThat(resp.severityMismatches()).isEqualTo(1);
        assertThat(resp.differences().get(0).differenceType()).isEqualTo(VerificationDifferenceType.SEVERITY_MISMATCH);
    }

    @Test
    @DisplayName("9. Deterministic verification ID is consistently formatted as verify-<32-hex>")
    void deterministicVerificationId_isConsistent() {
        String scenarioId = "scen-test-09";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result1 = verificationService.verifyScenario(scenarioId);
        String id1 = result1.verification().verificationId();

        assertThat(id1).startsWith("verify-");
        assertThat(id1.length()).isEqualTo(7 + 32); // verify- + 32 hex chars

        // Run again
        var result2 = verificationService.verifyScenario(scenarioId);
        String id2 = result2.verification().verificationId();

        assertThat(id1).isEqualTo(id2);
    }

    @Test
    @DisplayName("10. Repeated verification is idempotent and returns 200 without saving duplicate")
    void repeatedVerification_isIdempotent() {
        String scenarioId = "scen-test-10";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        ReplayVerification existing = new ReplayVerification(
                "verify-1234567890abcdef1234567890abcdef",
                scenarioId,
                ReplayVerificationStatus.PASSED,
                3, 3, 2, 2, 200L, 200L, 3, 0, 0, 0, 0, 0, "Already verified"
        );

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.of(existing));
        when(differenceRepository.findAllByVerificationIdOrderBySequenceNumberAsc(anyString()))
                .thenReturn(Collections.emptyList());

        var result = verificationService.verifyScenario(scenarioId);

        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.verification().verificationId()).isEqualTo(existing.getVerificationId());
        verify(verificationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("11. Unknown scenario throws ResourceNotFoundException")
    void unknownScenario_throwsResourceNotFoundException() {
        when(scenarioRepository.findByScenarioId("scen-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verificationService.verifyScenario("scen-unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Scenario not found with id: scen-unknown");
    }

    @Test
    @DisplayName("12. Original telemetry remains untouched during verification")
    void originalTelemetry_remainsUntouched() {
        String scenarioId = "scen-test-12";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        verificationService.verifyScenario(scenarioId);

        // Verification service only interacts with scenario/verification repositories, never mutating telemetry
        verify(scenarioEventRepository, never()).save(any());
        verify(scenarioEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("13. Replay scenario entity remains unchanged during verification")
    void replayScenario_remainsUnchanged() {
        String scenarioId = "scen-test-13";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        verificationService.verifyScenario(scenarioId);

        verify(scenarioRepository, never()).save(any());
        verify(scenarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("14. Non-critical severity difference results in PARTIAL status")
    void nonCriticalSeverityDifference_resultsInPartial() {
        String scenarioId = "scen-test-14";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(differenceRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // WARN instead of INFO for sequence 1 (both non-critical failure severity)
        List<ReplayObservedEvent> observations = List.of(
                new ReplayObservedEvent(1, "evt-1", "order-service", EventType.REQUEST, Severity.WARN, 0L),
                new ReplayObservedEvent(2, "evt-2", "payment-service", EventType.DATABASE, Severity.ERROR, 80L),
                new ReplayObservedEvent(3, "evt-3", "order-service", EventType.EXTERNAL_CALL, Severity.FATAL, 200L)
        );

        var result = verificationService.verifyScenarioWithObservations(scenarioId, observations, 200L);

        assertThat(result.verification().status()).isEqualTo(ReplayVerificationStatus.PARTIAL);
    }

    @Test
    @DisplayName("15. Critical severity difference results in FAILED status")
    void criticalSeverityDifference_resultsInFailed() {
        String scenarioId = "scen-test-15";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(differenceRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Error changed to INFO (critical severity mismatch)
        List<ReplayObservedEvent> observations = List.of(
                new ReplayObservedEvent(1, "evt-1", "order-service", EventType.REQUEST, Severity.INFO, 0L),
                new ReplayObservedEvent(2, "evt-2", "payment-service", EventType.DATABASE, Severity.INFO, 80L),
                new ReplayObservedEvent(3, "evt-3", "order-service", EventType.EXTERNAL_CALL, Severity.FATAL, 200L)
        );

        var result = verificationService.verifyScenarioWithObservations(scenarioId, observations, 200L);

        assertThat(result.verification().status()).isEqualTo(ReplayVerificationStatus.FAILED);
    }

    @Test
    @DisplayName("16. Multiple mismatch types coexist deterministically")
    void multipleMismatchTypes_coexistDeterministically() {
        String scenarioId = "scen-test-16";
        ReplayScenario scenario = createSampleScenario(scenarioId, 2);
        List<ReplayScenarioEvent> events = createSampleScenarioEvents(scenarioId);

        when(scenarioRepository.findByScenarioId(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioEventRepository.findAllByScenarioIdOrderBySequenceNumberAsc(scenarioId)).thenReturn(events);
        when(verificationRepository.findByVerificationId(anyString())).thenReturn(Optional.empty());
        when(verificationRepository.saveAndFlush(any(ReplayVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(differenceRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Sequence 1: Service mismatch + event type mismatch + severity mismatch
        // Sequence 2: Matches
        // Sequence 3: Missing
        // Sequence 4: Unexpected
        List<ReplayObservedEvent> observations = List.of(
                new ReplayObservedEvent(1, "evt-1", "custom-service", EventType.RESPONSE, Severity.WARN, 0L),
                new ReplayObservedEvent(2, "evt-2", "payment-service", EventType.DATABASE, Severity.ERROR, 80L),
                new ReplayObservedEvent(4, "evt-extra", "notification-service", EventType.EXTERNAL_CALL, Severity.INFO, 300L)
        );

        var result = verificationService.verifyScenarioWithObservations(scenarioId, observations, 300L);

        var resp = result.verification();
        assertThat(resp.status()).isEqualTo(ReplayVerificationStatus.FAILED);
        assertThat(resp.serviceMismatches()).isEqualTo(1);
        assertThat(resp.eventTypeMismatches()).isEqualTo(1);
        assertThat(resp.severityMismatches()).isEqualTo(1);
        assertThat(resp.eventsMissing()).isEqualTo(1);
        assertThat(resp.eventsUnexpected()).isEqualTo(1);
        assertThat(resp.eventsMatched()).isEqualTo(1);
        assertThat(resp.differences()).hasSize(5); // 3 mismatches on seq 1 + 1 missing seq 3 + 1 unexpected seq 4
    }
}
