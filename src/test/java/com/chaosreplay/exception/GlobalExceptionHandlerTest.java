package com.chaosreplay.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestExceptionController.class)
@Import({GlobalExceptionHandlerTest.TestExceptionController.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    @RequestMapping("/test/errors")
    static class TestExceptionController {

        @GetMapping("/illegal-argument")
        public void throwIllegalArgument() {
            throw new IllegalArgumentException("Invalid argument provided");
        }

        @GetMapping("/unexpected")
        public void throwUnexpected() {
            throw new RuntimeException("Sensitive database stack trace details");
        }
    }

    @Test
    @DisplayName("IllegalArgumentException returns 400 with structured ApiErrorResponse")
    void handleIllegalArgumentException_returns400() throws Exception {
        mockMvc.perform(get("/test/errors/illegal-argument")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid argument provided"))
                .andExpect(jsonPath("$.path").value("/test/errors/illegal-argument"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Unhandled Exception returns 500 with generic message and no leaked stack trace")
    void handleUnexpectedException_returns500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected internal error occurred"))
                .andExpect(jsonPath("$.path").value("/test/errors/unexpected"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(content().string(not(containsString("Sensitive database stack trace details"))));
    }
}
