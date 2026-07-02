package com.stockpile.picking.dto;

import com.stockpile.picking.domain.OrderLine;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Request/response payload for one {@link OrderLine}. */
public record OrderLineDto(
		Long id,
		@NotNull Long skuId,
		@NotNull @Positive Integer qty) {

	public static OrderLineDto from(OrderLine line) {
		return new OrderLineDto(line.getId(), line.getSku().getId(), line.getQty());
	}
}
