package com.chaosreplay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ChaosReplayApplicationTests {

    @Test
    @DisplayName("Application context starts successfully without external dependencies")
    void contextLoads() {
    }
}

