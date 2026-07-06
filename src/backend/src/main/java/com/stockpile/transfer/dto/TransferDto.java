package com.stockpile.transfer.dto;

import java.time.Instant;

import com.stockpile.transfer.domain.Transfer;
import com.stockpile.transfer.domain.TransferStatus;

/**
 * Response view of a {@link Transfer}. Carries the lot and both warehouses so
 * the "đang chuyển" list needs no extra lookups; the destination bin is only
 * known once received, hence the inbound movement id may be null.
 */
public record TransferDto(
		Long id,
		Long lotId,
		String skuCode,
		Long fromWarehouseId,
		Long toWarehouseId,
		TransferStatus status,
		Long outboundMovementId,
		Long inboundMovementId,
		Instant createdAt,
		Instant completedAt) {

	public static TransferDto from(Transfer t) {
		return new TransferDto(
				t.getId(),
				t.getLot().getId(),
				t.getLot().getSku().getCode(),
				t.getFromWarehouse().getId(),
				t.getToWarehouse().getId(),
				t.getStatus(),
				t.getOutboundMovement().getId(),
				t.getInboundMovement() == null ? null : t.getInboundMovement().getId(),
				t.getCreatedAt(),
				t.getCompletedAt());
	}
}
