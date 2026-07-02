package com.stockpile.picking.domain;

/** Lifecycle of a pick order. Planning is proposal-only; execution is separate. */
public enum OrderStatus {
	/** Just created; not yet planned. */
	OPEN,
	/** A pick-list has been proposed for it. */
	PLANNED,
	/** All lines have been picked (movements recorded). */
	PICKED,
	/** Abandoned. */
	CANCELLED
}
