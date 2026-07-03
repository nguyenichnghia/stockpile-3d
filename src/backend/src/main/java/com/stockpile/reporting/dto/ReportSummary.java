package com.stockpile.reporting.dto;

/**
 * Warehouse KPIs at a glance, aggregated from the placement projection, the
 * movement ledger and open orders. All counts are unit-based (lots = cases /
 * pallets), matching the rest of the system.
 */
public record ReportSummary(
		long totalBins,
		long occupiedBins,
		/** occupiedBins / totalBins, 0.0 for an empty warehouse. */
		double fillRate,
		long activeLots,
		/** Placed lots with at least one blocker (on-top or in-front, lane-local). */
		long blockedLots,
		/** Placed lots expiring within the FEFO horizon (30 days), today included. */
		long expiringSoon,
		/** Placed lots already past their expiry date. */
		long expired,
		long openOrders,
		/** Ledger entries recorded since UTC midnight. */
		long movementsToday) {
}
