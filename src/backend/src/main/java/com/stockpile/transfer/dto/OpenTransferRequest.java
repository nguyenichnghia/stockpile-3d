package com.stockpile.transfer.dto;

import jakarta.validation.constraints.NotNull;

/** Opens a transfer of a placed lot to another warehouse (ADR-0010). */
public record OpenTransferRequest(
		@NotNull Long lotId,
		@NotNull Long toWarehouseId) {
}
