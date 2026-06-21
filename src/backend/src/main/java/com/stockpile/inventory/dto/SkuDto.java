package com.stockpile.inventory.dto;

import java.math.BigDecimal;

import com.stockpile.inventory.domain.HandlingType;
import com.stockpile.inventory.domain.Sku;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Request/response payload for {@link Sku}. */
public record SkuDto(
		Long id,
		@NotBlank String code,
		@NotBlank String name,
		@NotNull @Positive BigDecimal w,
		@NotNull @Positive BigDecimal d,
		@NotNull @Positive BigDecimal h,
		@NotNull @Positive BigDecimal weight,
		@NotNull HandlingType handling) {

	public static SkuDto from(Sku sku) {
		return new SkuDto(sku.getId(), sku.getCode(), sku.getName(),
				sku.getW(), sku.getD(), sku.getH(), sku.getWeight(), sku.getHandling());
	}

	/** Copies request fields onto an entity (id is managed by the store). */
	public void applyTo(Sku sku) {
		sku.setCode(code);
		sku.setName(name);
		sku.setW(w);
		sku.setD(d);
		sku.setH(h);
		sku.setWeight(weight);
		sku.setHandling(handling);
	}
}
