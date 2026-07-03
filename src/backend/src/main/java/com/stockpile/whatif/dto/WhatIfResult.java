package com.stockpile.whatif.dto;

/**
 * Outcome of a layout what-if: the same stock measured in the current layout
 * and in the simulated one, side by side. Pure simulation — nothing persisted.
 */
public record WhatIfResult(LayoutMetrics current, LayoutMetrics simulated) {

	/**
	 * Health of one layout holding the warehouse's lots. {@code unplacedLots}
	 * counts lots the simulated layout could not fit anywhere (always 0 for the
	 * current layout — they are physically placed).
	 */
	public record LayoutMetrics(
			long bins,
			long placedLots,
			long unplacedLots,
			/** Lots that need at least one relocation before they can be picked. */
			long blockedLots,
			double fillRate,
			/** Mean distance from the dock (origin) over occupied bins; 0 when empty. */
			double avgDistToDock) {
	}
}
