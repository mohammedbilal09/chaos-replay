package com.chaosreplay;

import com.chaosreplay.repository.ReplayScenarioEventRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import com.chaosreplay.repository.ReplayVerificationDifferenceRepository;
import com.chaosreplay.repository.ReplayVerificationRepository;
import com.chaosreplay.repository.TelemetryEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class ChaosReplayApplicationTests {

    @MockitoBean
    private TelemetryEventRepository telemetryEventRepository;

    @MockitoBean
    private ReplayScenarioRepository replayScenarioRepository;

    @MockitoBean
    private ReplayScenarioEventRepository replayScenarioEventRepository;

    @MockitoBean
    private ReplayVerificationRepository replayVerificationRepository;

    @MockitoBean
    private ReplayVerificationDifferenceRepository replayVerificationDifferenceRepository;

    @MockitoBean
    private com.chaosreplay.repository.FailureAnalysisRepository failureAnalysisRepository;

    @MockitoBean
    private com.chaosreplay.repository.FailureAnalysisCandidateRepository failureAnalysisCandidateRepository;

    @MockitoBean
    private com.chaosreplay.repository.FailureAnalysisEvidenceRepository failureAnalysisEvidenceRepository;

    @Test
    @DisplayName("Application context starts successfully without external dependencies")
    void contextLoads() {
    }
}

