package com.stockpile.picking.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.stockpile.inventory.domain.HandlingType;

/**
 * Pure lot-selection core for the picking engine (no DB — testable like
 * {@link com.stockpile.relocation.service.BlockingGraph}). Given the candidate
 * lots for one order line and the SKU's handling policy, it decides which lots
 * to take and in what order.
 *
 * <p><b>Ordering rule</b> (docs/01 §8.4, ADR-0006): the handling policy leads —
 * FEFO takes the earliest {@code expiry} first, FIFO the oldest lot first — and
 * ties are broken by least-blocked (fewest lots in the way), the operator's
 * "easiest to retrieve" preference applied <i>within</i> equal expiry/age. This
 * honours the perishable rule while still preferring easy lots when it is free
 * to do so; near-expiry stock is never stranded behind an easier lot.
 */
public final class PickPlanner {

	private PickPlanner() {
	}

	/**
	 * One pickable lot and the facts the ordering needs. {@code age} is a proxy
	 * for "received/oldest first" under FIFO (earlier is older); {@code expiry}
	 * drives FEFO (nulls last — no expiry means least urgent). {@code blockers}
	 * is how many lots must move before this one is reachable.
	 */
	public record Candidate(long lotId, long binId, LocalDate expiry, Instant age, int blockers) {
	}

	/**
	 * Picks lots for a line until {@code qty} is met (or candidates run out),
	 * ordered by the rule above. Returns the chosen candidates in pick order; its
	 * size may be less than {@code qty} (a shortfall).
	 */
	public static List<Candidate> select(List<Candidate> candidates, HandlingType handling, int qty) {
		return candidates.stream()
				.sorted(order(handling))
				.limit(Math.max(qty, 0))
				.toList();
	}

	/** The pick order for a handling policy: policy key first, then least-blocked. */
	static Comparator<Candidate> order(HandlingType handling) {
		Comparator<Candidate> byPolicy = handling == HandlingType.FEFO
				? Comparator.comparing(Candidate::expiry, Comparator.nullsLast(Comparator.naturalOrder()))
				: Comparator.comparing(Candidate::age, Comparator.nullsLast(Comparator.naturalOrder()));
		return byPolicy.thenComparingInt(Candidate::blockers);
	}
}
