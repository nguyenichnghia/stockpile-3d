package com.stockpile.scan.dto;

import java.util.List;

/**
 * What a scanned code resolves to. v1 barcodes are derived identifiers — no
 * dedicated barcode column (ADR-0007): {@code LOT-{id}} names a lot and the
 * five-part {@code zone-aisle-rack-level-bin} code names a bin. Exactly one of
 * {@code lot}/{@code bin} is non-null when {@code found}; a syntactically valid
 * code with no matching row keeps its type but {@code found=false}.
 */
public record ScanResult(String code, Type type, boolean found, LotInfo lot, BinInfo bin) {

	public enum Type {
		/** Code shaped like {@code LOT-{id}}. */
		LOT,
		/** Code shaped like {@code zone-aisle-rack-level-bin}. */
		BIN,
		/** Neither shape — nothing to resolve. */
		UNKNOWN
	}

	/** A resolved lot and where it currently sits ({@code binId} null when unplaced). */
	public record LotInfo(long id, String sku, Long binId, String binCode, String laneId) {
	}

	/** A resolved bin and the lots currently placed in it (empty list when empty). */
	public record BinInfo(long id, String code, String laneId, List<Long> lotIds) {
	}

	public static ScanResult lot(String code, LotInfo lot) {
		return new ScanResult(code, Type.LOT, true, lot, null);
	}

	public static ScanResult bin(String code, BinInfo bin) {
		return new ScanResult(code, Type.BIN, true, null, bin);
	}

	public static ScanResult notFound(String code, Type type) {
		return new ScanResult(code, type, false, null, null);
	}
}
