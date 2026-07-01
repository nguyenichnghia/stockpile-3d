package com.stockpile.locate.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of locating a SKU: every current placement of a lot of that SKU, so
 * the 3D scene can highlight those bins and dim the rest (docs/warehouse-setup
 * feature "tra cứu/định vị mã hàng"). Read-only — search never mutates state.
 */
public record LocateResult(String sku, int matchCount, List<Match> matches) {

	/** One placed lot of the searched SKU, with enough pose to highlight it. */
	public record Match(long lotId, long binId, BigDecimal x, BigDecimal y, BigDecimal z) {
	}
}
