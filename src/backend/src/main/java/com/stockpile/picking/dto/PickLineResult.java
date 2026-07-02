package com.stockpile.picking.dto;

/**
 * How one order line was fulfilled by the plan. {@code fulfilled} is how many
 * units the plan actually allocated; {@code shortfall} = requested − fulfilled
 * (0 when there is enough stock).
 */
public record PickLineResult(String sku, int requested, int fulfilled, int shortfall) {

	public static PickLineResult of(String sku, int requested, int fulfilled) {
		return new PickLineResult(sku, requested, fulfilled, requested - fulfilled);
	}
}
