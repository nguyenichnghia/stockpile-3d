package com.stockpile.locate.dto;

import java.math.BigDecimal;

/**
 * Result of locating a bin by its full code (zone-aisle-rack-level-bin). When
 * {@code found} is true, the pose lets the 3D scene highlight that bin frame —
 * even if the bin is empty. Read-only.
 */
public record BinLocateResult(
		String code,
		boolean found,
		Long binId,
		BigDecimal x,
		BigDecimal y,
		BigDecimal z) {

	public static BinLocateResult notFound(String code) {
		return new BinLocateResult(code, false, null, null, null, null);
	}
}
