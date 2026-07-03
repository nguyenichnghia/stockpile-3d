package com.stockpile.reporting.dto;

import java.time.LocalDate;

/**
 * Ledger throughput for one (UTC day, movement type) pair. Days with no
 * movements produce no rows — consumers fill the gaps when charting.
 */
public record MovementDaily(LocalDate date, String type, long count) {
}
