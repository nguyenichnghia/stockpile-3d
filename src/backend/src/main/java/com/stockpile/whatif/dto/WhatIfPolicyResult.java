package com.stockpile.whatif.dto;

import com.stockpile.putaway.service.PutawayWeights;
import com.stockpile.whatif.dto.WhatIfResult.LayoutMetrics;

/**
 * Outcome of a policy what-if: the warehouse's real bins, re-filled twice — once
 * with the baseline SLAP weights and once with candidate weights — measured side
 * by side (ADR-0008). Layout is held fixed, so any difference is the weights'
 * doing, not a change of bins. Pure simulation — nothing persisted.
 *
 * <p>The two weight sets are echoed back so the UI can show exactly what was
 * compared (the candidate is the client's partial spec merged onto the baseline).
 */
public record WhatIfPolicyResult(
		LayoutMetrics baseline,
		LayoutMetrics candidate,
		PutawayWeights baselineWeights,
		PutawayWeights candidateWeights) {
}
