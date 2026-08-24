package com.merchtyl.idempotency;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile("test")
class TestIdempotencyController {
    private static final String ENDPOINT = "POST /api/v1/test/idempotency";

    private final IdempotencyService idempotencyService;
    private final TestIdempotencyOperationRecorder recorder;

    TestIdempotencyController(
            IdempotencyService idempotencyService,
            TestIdempotencyOperationRecorder recorder) {
        this.idempotencyService = idempotencyService;
        this.recorder = recorder;
    }

    @PostMapping("/api/v1/test/idempotency")
    ResponseEntity<String> create(
            @RequestHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestHeader("X-Test-User-Id") UUID userId,
            @RequestParam(defaultValue = "201") int status,
            @RequestParam(defaultValue = "0") long delayMs,
            @RequestBody(required = false) String body) {
        IdempotencyResult result = idempotencyService.execute(userId, ENDPOINT, idempotencyKey, body, () -> {
            sleep(delayMs);
            int operationCount = recorder.recordOperation();
            return new IdempotencyOperationResponse(
                    status,
                    MediaType.APPLICATION_JSON_VALUE,
                    """
                            {"operationCount":%d,"requestBody":%s}
                            """.formatted(operationCount, jsonString(body)));
        });
        return ResponseEntity.status(result.status())
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }

    private static void sleep(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating idempotent operation", exception);
        }
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
