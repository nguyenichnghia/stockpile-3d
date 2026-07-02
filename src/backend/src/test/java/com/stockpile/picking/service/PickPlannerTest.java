package com.stockpile.picking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockpile.inventory.domain.HandlingType;
import com.stockpile.picking.service.PickPlanner.Candidate;

/** Pure unit tests for lot selection — no DB, runs in milliseconds. */
class PickPlannerTest {

	private static final LocalDate D1 = LocalDate.of(2026, 1, 1);
	private static final LocalDate D2 = LocalDate.of(2026, 6, 1);
	private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant T2 = Instant.parse("2026-06-01T00:00:00Z");

	@Test
	void fefoTakesEarliestExpiryFirst() {
		Candidate soon = new Candidate(1, 10, D1, T2, 0);
		Candidate later = new Candidate(2, 20, D2, T1, 0);

		List<Candidate> chosen = PickPlanner.select(List.of(later, soon), HandlingType.FEFO, 1);

		assertThat(chosen).extracting(Candidate::lotId).containsExactly(1L);
	}

	@Test
	void fifoTakesOldestLotFirst() {
		Candidate old = new Candidate(1, 10, D2, T1, 0);
		Candidate fresh = new Candidate(2, 20, D1, T2, 0);

		List<Candidate> chosen = PickPlanner.select(List.of(fresh, old), HandlingType.FIFO, 1);

		assertThat(chosen).extracting(Candidate::lotId).containsExactly(1L);
	}

	@Test
	void tieOnPolicyKeyIsBrokenByLeastBlocked() {
		// Same expiry — the least-blocked lot wins (operator's "easiest" preference).
		Candidate blocked = new Candidate(1, 10, D1, T1, 3);
		Candidate free = new Candidate(2, 20, D1, T1, 0);

		List<Candidate> chosen = PickPlanner.select(List.of(blocked, free), HandlingType.FEFO, 1);

		assertThat(chosen).extracting(Candidate::lotId).containsExactly(2L);
	}

	@Test
	void expiryNeverLosesToLeastBlocked() {
		// A near-expiry but heavily blocked lot still comes before an easy far one:
		// perishable stock is not stranded behind an easier lot.
		Candidate nearExpiryBlocked = new Candidate(1, 10, D1, T1, 5);
		Candidate farExpiryFree = new Candidate(2, 20, D2, T1, 0);

		List<Candidate> chosen = PickPlanner.select(
				List.of(farExpiryFree, nearExpiryBlocked), HandlingType.FEFO, 1);

		assertThat(chosen).extracting(Candidate::lotId).containsExactly(1L);
	}

	@Test
	void takesUpToQtyInOrder() {
		Candidate a = new Candidate(1, 10, D1, T1, 0);
		Candidate b = new Candidate(2, 20, D2, T2, 0);
		Candidate c = new Candidate(3, 30, LocalDate.of(2026, 12, 1), T2, 0);

		List<Candidate> chosen = PickPlanner.select(List.of(c, a, b), HandlingType.FEFO, 2);

		assertThat(chosen).extracting(Candidate::lotId).containsExactly(1L, 2L);
	}

	@Test
	void selectsFewerThanQtyOnShortfall() {
		Candidate only = new Candidate(1, 10, D1, T1, 0);

		List<Candidate> chosen = PickPlanner.select(List.of(only), HandlingType.FEFO, 3);

		assertThat(chosen).hasSize(1);
	}

	@Test
	void nullExpirySortsLast() {
		Candidate noExpiry = new Candidate(1, 10, null, T1, 0);
		Candidate hasExpiry = new Candidate(2, 20, D2, T1, 0);

		List<Candidate> chosen = PickPlanner.select(List.of(noExpiry, hasExpiry), HandlingType.FEFO, 1);

		assertThat(chosen).extracting(Candidate::lotId).containsExactly(2L);
	}
}
