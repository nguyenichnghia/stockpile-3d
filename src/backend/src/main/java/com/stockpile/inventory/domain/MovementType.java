package com.stockpile.inventory.domain;

/** Kind of physical movement recorded in the append-only ledger. */
public enum MovementType {
	INBOUND,
	PUTAWAY,
	RELOCATE,
	PICK,
	OUTBOUND
}
