package com.chaosreplay;

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

    @Test
    @DisplayName("Application context starts successfully without external dependencies")
    void contextLoads() {
    }
}

