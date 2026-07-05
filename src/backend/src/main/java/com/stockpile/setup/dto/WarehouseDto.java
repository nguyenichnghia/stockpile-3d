package com.stockpile.setup.dto;

import java.time.Instant;

import com.stockpile.inventory.domain.Warehouse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response payload for a {@link Warehouse}. */
public record WarehouseDto(
		Long id,
		@NotBlank @Size(max = 32) String code,
		@NotBlank @Size(max = 255) String name,
		/** Scan-enforcement policy; null on create means off. */
		Boolean requireScan,
		Instant createdAt) {

	public static WarehouseDto from(Warehouse w) {
		return new WarehouseDto(w.getId(), w.getCode(), w.getName(), w.isRequireScan(), w.getCreatedAt());
	}
}
