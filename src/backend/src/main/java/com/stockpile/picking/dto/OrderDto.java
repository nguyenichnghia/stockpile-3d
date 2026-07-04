package com.stockpile.picking.dto;

import java.time.Instant;
import java.util.List;

import com.stockpile.picking.domain.OrderStatus;
import com.stockpile.picking.domain.PickOrder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** Request/response payload for a {@link PickOrder} and its lines. */
public record OrderDto(
		Long id,
		@NotBlank String code,
		@NotNull Long warehouseId,
		OrderStatus status,
		Instant createdAt,
		@NotEmpty @Valid List<OrderLineDto> lines) {

	public static OrderDto from(PickOrder order) {
		return new OrderDto(
				order.getId(),
				order.getCode(),
				order.getWarehouse().getId(),
				order.getStatus(),
				order.getCreatedAt(),
				order.getLines().stream().map(OrderLineDto::from).toList());
	}
}
