package com.chaosreplay.api;

import com.chaosreplay.api.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final String SERVICE_NAME = "chaosreplay";
    private static final String STATUS_UP = "UP";

    public HealthController() {
    }

    @GetMapping
    public ResponseEntity<HealthResponse> getHealth() {
        return ResponseEntity.ok(new HealthResponse(STATUS_UP, SERVICE_NAME));
    }
}

