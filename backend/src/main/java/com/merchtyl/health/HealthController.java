package com.merchtyl.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    @GetMapping
    HealthResponse health() {
        return new HealthResponse("ok", "merchtyl-backend", Instant.now());
    }
}
