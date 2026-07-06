package com.stockpile.whatif.dto;

import com.stockpile.putaway.service.PutawayWeights;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Candidate SLAP weights for a policy what-if (docs/01 §8.2). Every field is
 * optional: a null keeps the warehouse's configured default for that term, so
 * a client can vary one weight in isolation. All weights are cost multipliers —
 * non-negative, lower total score = better slot.
 */
public record PutawayWeightsDto(
		@PositiveOrZero Double distToDock,
		@PositiveOrZero Double blockingPenalty,
		@PositiveOrZero Double retrievalMisalignment,
		@PositiveOrZero Double fitPenalty) {

	/**
	 * Resolves this partial spec against a baseline, keeping the baseline value
	 * wherever a field is null. Note: {@link PutawayWeights} treats 0 as "unset"
	 * and swaps in its own default, so an explicit 0 here cannot force a term
	 * off — a deliberate limitation matching how weights are configured.
	 */
	public PutawayWeights merge(PutawayWeights base) {
		return new PutawayWeights(
				distToDock != null ? distToDock : base.distToDock(),
				blockingPenalty != null ? blockingPenalty : base.blockingPenalty(),
				retrievalMisalignment != null ? retrievalMisalignment : base.retrievalMisalignment(),
				fitPenalty != null ? fitPenalty : base.fitPenalty());
	}
}
