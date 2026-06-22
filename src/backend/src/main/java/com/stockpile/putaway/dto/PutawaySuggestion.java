package com.stockpile.putaway.dto;

import java.util.List;

/**
 * SLAP result: the recommended bin for a lot plus the ranked alternatives.
 * Lower score is better. A proposal only — putaway is confirmed separately.
 */
public record PutawaySuggestion(
		long lotId,
		Long recommendedBinId,
		List<ScoredBin> candidates) {

	/** One scored candidate slot. */
	public record ScoredBin(long binId, double score) {
	}
}
