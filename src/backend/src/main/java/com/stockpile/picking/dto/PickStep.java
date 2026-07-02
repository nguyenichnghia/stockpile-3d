package com.stockpile.picking.dto;

/**
 * One action in a pick-list. A {@link Kind#RELOCATE} step clears a blocker
 * (move {@code lotId} from {@code fromBinId} to {@code toBinId}); a
 * {@link Kind#PICK} step retrieves the target lot from {@code fromBinId}
 * ({@code toBinId} is null). Proposal only — executing it records movements.
 */
public record PickStep(Kind kind, long lotId, long fromBinId, Long toBinId) {

	public enum Kind {
		/** Move a blocking lot out of the way before the pick. */
		RELOCATE,
		/** Retrieve the requested lot. */
		PICK
	}

	public static PickStep relocate(long lotId, long fromBinId, long toBinId) {
		return new PickStep(Kind.RELOCATE, lotId, fromBinId, toBinId);
	}

	public static PickStep pick(long lotId, long fromBinId) {
		return new PickStep(Kind.PICK, lotId, fromBinId, null);
	}
}
