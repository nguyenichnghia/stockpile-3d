package com.stockpile.setup.dto;

import java.math.BigDecimal;

import com.stockpile.inventory.domain.AccessFace;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Parameters for generating a regular grid warehouse (docs/warehouse-setup.md
 * §2, "Cách A — Grid Generator"). Describes a rectangular block of slots:
 * {@code zones} zones, each with {@code aislesPerZone} aisles, each aisle with
 * {@code racksPerAisle} racks, each rack {@code levelsPerRack} levels tall and
 * {@code binsPerLevel} bins wide. Total slots = product of the five counts.
 *
 * <p>Coordinates are derived from the slot indices and {@code binSize}, with an
 * {@code aisleGap} walkway between aisles. All lots in one {@code (zone, aisle,
 * rack)} share a lane id, honoring the "blocking is lane-local" invariant.
 */
public record WarehouseGridSpec(
		@NotNull @Positive Integer zones,
		@NotNull @Positive Integer aislesPerZone,
		@NotNull @Positive Integer racksPerAisle,
		@NotNull @Positive Integer levelsPerRack,
		@NotNull @Positive Integer binsPerLevel,
		@NotNull @Positive BigDecimal binWidth,
		@NotNull @Positive BigDecimal binDepth,
		@NotNull @Positive BigDecimal binHeight,
		@NotNull @Positive BigDecimal aisleGap,
		@NotNull AccessFace accessFace) {

	/** Number of slots this spec will generate. */
	public long totalSlots() {
		return (long) zones * aislesPerZone * racksPerAisle * levelsPerRack * binsPerLevel;
	}
}
