package com.stockpile;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("""
		Full context load needs a live PostgreSQL (datasource + Flyway). \
		Re-enable once a test database is wired (e.g. Testcontainers) \
		alongside the first entities/migrations.""")
class StockpileBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
