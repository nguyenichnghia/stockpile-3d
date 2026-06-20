package com.stockpile.common;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal application-level health endpoint.
 *
 * <p>Independent of Spring Boot Actuator's {@code /actuator/health} so the app
 * always exposes a stable, app-owned liveness check. No business logic.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP");
	}
}
