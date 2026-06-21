package com.stockpile.inventory.dto;

import java.math.BigDecimal;

import com.stockpile.inventory.domain.Placement;

/**
 * Read-only view of a {@link Placement}. Placement is a projection of the
 * ledger and is never edited directly (see ADR-0003).
 */
public record PlacementDto(
		Long id,
		Long lotId,
		Long binId,
		BigDecimal x,
		BigDecimal y,
		BigDecimal z) {

	public static PlacementDto from(Placement p) {
		return new PlacementDto(p.getId(), p.getLot().getId(), p.getBin().getId(),
				p.getX(), p.getY(), p.getZ());
	}
}
