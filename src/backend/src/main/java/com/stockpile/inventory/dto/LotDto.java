package com.stockpile.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.stockpile.inventory.domain.Lot;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request/response payload for {@link Lot}. References its SKU by id; the
 * service resolves and validates it.
 */
public record LotDto(
		Long id,
		@NotNull Long skuId,
		@NotNull @Positive BigDecimal w,
		@NotNull @Positive BigDecimal d,
		@NotNull @Positive BigDecimal h,
		@NotNull @Positive BigDecimal weight,
		LocalDate expiry,
		Instant predictedRetrievalAt) {

	public static LotDto from(Lot lot) {
		return new LotDto(lot.getId(), lot.getSku().getId(),
				lot.getW(), lot.getD(), lot.getH(), lot.getWeight(),
				lot.getExpiry(), lot.getPredictedRetrievalAt());
	}
}
