package com.chaosreplay.api;

import com.chaosreplay.api.dto.ReplayVerificationResponse;
import com.chaosreplay.service.ReplayVerificationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing endpoints for verifying failure replays against original telemetry
 * and inspecting behavioral differences.
 */
@RestController
@RequestMapping("/api/v1/replay")
@Validated
public class ReplayVerificationController {

    private final ReplayVerificationService verificationService;

    public ReplayVerificationController(ReplayVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * Verifies a failure replay scenario against original telemetry.
     * Idempotent: returns 201 CREATED if newly verified, or 200 OK if an identical verification already exists.
     *
     * @param scenarioId deterministic scenario identifier
     * @return 201 CREATED or 200 OK with the verification response payload
     */
    @PostMapping(value = "/scenarios/{scenarioId}/verify", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReplayVerificationResponse> verifyScenario(
            @PathVariable @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-zA-Z0-9-_.:]+$") String scenarioId
    ) {
        ReplayVerificationService.ReplayVerificationResult result = verificationService.verifyScenario(scenarioId);
        if (result.newlyCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.verification());
        } else {
            return ResponseEntity.ok(result.verification());
        }
    }

    /**
     * Retrieves an existing replay verification result by its deterministic verification identifier.
     *
     * @param verificationId deterministic verification identifier
     * @return 200 OK with verification details and differences, or 404 NOT FOUND
     */
    @GetMapping(value = "/verifications/{verificationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReplayVerificationResponse> getVerification(
            @PathVariable @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-zA-Z0-9-_.:]+$") String verificationId
    ) {
        ReplayVerificationResponse response = verificationService.getVerification(verificationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all verification attempts for a specific replay scenario ordered by creation time descending.
     *
     * @param scenarioId deterministic scenario identifier
     * @return 200 OK with list of verification results, or 404 NOT FOUND if scenario does not exist
     */
    @GetMapping(value = "/scenarios/{scenarioId}/verifications", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ReplayVerificationResponse>> getVerificationsForScenario(
            @PathVariable @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-zA-Z0-9-_.:]+$") String scenarioId
    ) {
        List<ReplayVerificationResponse> responses = verificationService.getVerificationsForScenario(scenarioId);
        return ResponseEntity.ok(responses);
    }
}
