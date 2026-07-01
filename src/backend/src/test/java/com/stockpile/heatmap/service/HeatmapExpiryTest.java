package com.stockpile.heatmap.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Pure unit tests for the expiry-urgency math — no DB, runs in milliseconds. */
class HeatmapExpiryTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 7, 1);

	@Test
	void noExpiryIsCool() {
		assertThat(HeatmapService.expiryUrgency(null, TODAY)).isZero();
	}

	@Test
	void farExpiryIsCool() {
		// 30+ days out => 0.
		assertThat(HeatmapService.expiryUrgency(TODAY.plusDays(30), TODAY)).isZero();
		assertThat(HeatmapService.expiryUrgency(TODAY.plusDays(60), TODAY)).isZero();
	}

	@Test
	void expiryDayIsHot() {
		assertThat(HeatmapService.expiryUrgency(TODAY, TODAY)).isEqualTo(1.0);
	}

	@Test
	void pastExpiryIsClampedHot() {
		assertThat(HeatmapService.expiryUrgency(TODAY.minusDays(5), TODAY)).isEqualTo(1.0);
	}

	@Test
	void halfwayIsHalf() {
		// 15 days left over a 30-day horizon => 0.5.
		assertThat(HeatmapService.expiryUrgency(TODAY.plusDays(15), TODAY)).isEqualTo(0.5);
	}
}
