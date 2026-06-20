package com.stockpile;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application context against a real PostgreSQL container.
 * Verifies that Flyway applies the migrations and that the JPA entities map
 * cleanly onto the resulting schema (ddl-auto=validate). Requires Docker.
 */
@SpringBootTest
@Testcontainers
class StockpileBackendApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Test
	void contextLoads() {
	}

}
