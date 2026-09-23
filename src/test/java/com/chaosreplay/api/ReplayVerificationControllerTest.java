package com.chaosreplay.api;

import com.chaosreplay.api.dto.ReplayVerificationDifferenceResponse;
import com.chaosreplay.api.dto.ReplayVerificationResponse;
import com.chaosreplay.domain.ReplayVerificationStatus;
import com.chaosreplay.domain.VerificationDifferenceType;
import com.chaosreplay.exception.GlobalExceptionHandler;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.service.ReplayVerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReplayVerificationController.class)
@Import(GlobalExceptionHandler.class)
class ReplayVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReplayVerificationService verificationService;

    @Test
    @DisplayName("1. POST /api/v1/replay/scenarios/{id}/verify returns 201 Created on fresh verification")
    void verifyScenario_fresh_returns201() throws Exception {
        String scenarioId = "scen-verify-1";
        ReplayVerificationResponse response = new ReplayVerificationResponse(
                "verify-11111111222222223333333344444444",
                scenarioId,
                ReplayVerificationStatus.PASSED,
                3, 3, 1, 1, 200L, 200L,
                3, 0, 0, 0, 0, 0,
                "Replay verified successfully",
                Instant.now(),
                List.of()
        );

        when(verificationService.verifyScenario(scenarioId))
                .thenReturn(new ReplayVerificationService.ReplayVerificationResult(response, true));

        mockMvc.perform(post("/api/v1/replay/scenarios/{scenarioId}/verify", scenarioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.verificationId").value("verify-11111111222222223333333344444444"))
                .andExpect(jsonPath("$.scenarioId").value(scenarioId))
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.eventsMatched").value(3))
                .andExpect(jsonPath("$.eventsMissing").value(0));
    }

    @Test
    @DisplayName("2. Repeat POST /api/v1/replay/scenarios/{id}/verify returns 200 OK on idempotent verification")
    void verifyScenario_repeat_returns200() throws Exception {
        String scenarioId = "scen-verify-repeat";
        ReplayVerificationResponse response = new ReplayVerificationResponse(
                "verify-repeat-1234567890abcdef123456",
                scenarioId,
                ReplayVerificationStatus.PASSED,
                3, 3, 1, 1, 200L, 200L,
                3, 0, 0, 0, 0, 0,
                "Replay verified successfully",
                Instant.now(),
                List.of()
        );

        when(verificationService.verifyScenario(scenarioId))
                .thenReturn(new ReplayVerificationService.ReplayVerificationResult(response, false));

        mockMvc.perform(post("/api/v1/replay/scenarios/{scenarioId}/verify", scenarioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.verificationId").value("verify-repeat-1234567890abcdef123456"))
                .andExpect(jsonPath("$.scenarioId").value(scenarioId))
                .andExpect(jsonPath("$.status").value("PASSED"));
    }

    @Test
    @DisplayName("3. GET /api/v1/replay/verifications/{id} returns 200 OK with verification details")
    void getVerification_exists_returns200() throws Exception {
        String verificationId = "verify-get-1";
        ReplayVerificationDifferenceResponse diff = new ReplayVerificationDifferenceResponse(
                2, VerificationDifferenceType.SEVERITY_MISMATCH, "evt-2", "evt-2",
                "payment-service", "payment-service", "DATABASE_QUERY", "DATABASE_QUERY",
                "ERROR", "WARN", "Severity mismatch"
        );
        ReplayVerificationResponse response = new ReplayVerificationResponse(
                verificationId,
                "scen-1",
                ReplayVerificationStatus.FAILED,
                2, 2, 1, 0, 100L, 100L,
                1, 0, 0, 1, 0, 0,
                "Replay verification failed",
                Instant.now(),
                List.of(diff)
        );

        when(verificationService.getVerification(verificationId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/replay/verifications/{verificationId}", verificationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationId").value(verificationId))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.differences[0].differenceType").value("SEVERITY_MISMATCH"))
                .andExpect(jsonPath("$.differences[0].expectedSeverity").value("ERROR"))
                .andExpect(jsonPath("$.differences[0].actualSeverity").value("WARN"));
    }

    @Test
    @DisplayName("4. GET /api/v1/replay/verifications/{id} returns 404 when verification does not exist")
    void getVerification_missing_returns404() throws Exception {
        String verificationId = "verify-missing";
        when(verificationService.getVerification(verificationId))
                .thenThrow(new ResourceNotFoundException("Verification not found with id: " + verificationId));

        mockMvc.perform(get("/api/v1/replay/verifications/{verificationId}", verificationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Verification not found with id: verify-missing")));
    }

    @Test
    @DisplayName("5. GET /api/v1/replay/scenarios/{id}/verifications returns 200 OK with history")
    void getVerificationsForScenario_exists_returns200() throws Exception {
        String scenarioId = "scen-hist-1";
        ReplayVerificationResponse resp1 = new ReplayVerificationResponse(
                "verify-h1", scenarioId, ReplayVerificationStatus.PASSED,
                1, 1, 0, 0, 50L, 50L, 1, 0, 0, 0, 0, 0, "OK", Instant.now(), List.of()
        );

        when(verificationService.getVerificationsForScenario(scenarioId)).thenReturn(List.of(resp1));

        mockMvc.perform(get("/api/v1/replay/scenarios/{scenarioId}/verifications", scenarioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificationId").value("verify-h1"))
                .andExpect(jsonPath("$[0].scenarioId").value(scenarioId))
                .andExpect(jsonPath("$[0].status").value("PASSED"));
    }

    @Test
    @DisplayName("6. GET /api/v1/replay/scenarios/{id}/verifications returns 404 when scenario does not exist")
    void getVerificationsForScenario_unknownScenario_returns404() throws Exception {
        String scenarioId = "scen-missing";
        when(verificationService.getVerificationsForScenario(scenarioId))
                .thenThrow(new ResourceNotFoundException("Scenario not found with id: " + scenarioId));

        mockMvc.perform(get("/api/v1/replay/scenarios/{scenarioId}/verifications", scenarioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Scenario not found with id: scen-missing")));
    }
}

