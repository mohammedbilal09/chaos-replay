package com.chaosreplay.api;

import com.chaosreplay.api.dto.ValidationProbeRequest;
import com.chaosreplay.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ValidationTest.TestValidationController.class)
@Import({ValidationTest.TestValidationController.class, GlobalExceptionHandler.class})
class ValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    @RequestMapping("/test/validation")
    static class TestValidationController {

        @PostMapping
        public ResponseEntity<String> validateProbe(@Valid @RequestBody ValidationProbeRequest request) {
            return ResponseEntity.ok("Valid probe: " + request.probeName());
        }
    }

    @Test
    @DisplayName("Valid ValidationProbeRequest returns 200 OK")
    void validRequest_returns200() throws Exception {
        String json = """
                {
                    "probeName": "production-probe"
                }
                """;

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Blank probeName returns 400 Bad Request with validation message")
    void blankProbeName_returns400WithDetails() throws Exception {
        String json = """
                {
                    "probeName": ""
                }
                """;

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("probeName must not be blank")))
                .andExpect(jsonPath("$.path").value("/test/validation"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Too short probeName returns 400 Bad Request with size constraint message")
    void tooShortProbeName_returns400WithDetails() throws Exception {
        String json = """
                {
                    "probeName": "x"
                }
                """;

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("probeName must be between 2 and 50 characters")));
    }
}
