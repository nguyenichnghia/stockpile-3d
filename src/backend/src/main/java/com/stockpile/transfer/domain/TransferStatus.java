package com.stockpile.transfer.domain;

/**
 * Lifecycle of a cross-warehouse transfer (ADR-0010). v1 has no CANCELLED —
 * a transfer is opened (OUTBOUND recorded in A) and later completed (INBOUND
 * recorded in B); an in-transit transfer with no inbound yet is a valid state.
 */
public enum TransferStatus {
	IN_TRANSIT,
	COMPLETED
}
