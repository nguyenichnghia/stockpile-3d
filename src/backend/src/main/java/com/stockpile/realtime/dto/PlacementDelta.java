package com.stockpile.realtime.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The client-facing realtime delta pushed over STOMP when a lot's placement
 * changes. Covers add/move ({@link ChangeKind#UPSERT}) and removal
 * ({@link ChangeKind#REMOVE}). The frontend merges by {@code lotId} (a lot has
 * at most one placement), so this is the stable identity across add→move→remove.
 */
public record PlacementDelta(
		ChangeKind kind,
		Long lotId,
		Long binId,
		BigDecimal x,
		BigDecimal y,
		BigDecimal z,
		Instant ts) {

	public enum ChangeKind {
		/** Lot added to, or moved into, this bin. */
		UPSERT,
		/** Lot left this bin (picked/shipped, or relocated out of the lane). */
		REMOVE
	}

	public static PlacementDelta upsert(Long lotId, Long binId,
			BigDecimal x, BigDecimal y, BigDecimal z, Instant ts) {
		return new PlacementDelta(ChangeKind.UPSERT, lotId, binId, x, y, z, ts);
	}

	public static PlacementDelta remove(Long lotId, Instant ts) {
		return new PlacementDelta(ChangeKind.REMOVE, lotId, null, null, null, null, ts);
	}
}
