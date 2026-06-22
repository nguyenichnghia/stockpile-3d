package com.stockpile.putaway.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable weights for the SLAP scoring function (docs/01 §8.2). Configurable per
 * warehouse via {@code app.putaway.*} so the cost trade-offs can be adjusted
 * without code changes. Lower total score = better candidate slot.
 */
@ConfigurationProperties(prefix = "app.putaway")
public record PutawayWeights(
		double distToDock,
		double blockingPenalty,
		double retrievalMisalignment,
		double fitPenalty) {

	/** Sensible defaults if none are configured. */
	public PutawayWeights {
		distToDock = distToDock == 0 ? 1.0 : distToDock;
		blockingPenalty = blockingPenalty == 0 ? 10.0 : blockingPenalty;
		retrievalMisalignment = retrievalMisalignment == 0 ? 2.0 : retrievalMisalignment;
		fitPenalty = fitPenalty == 0 ? 5.0 : fitPenalty;
	}
}
