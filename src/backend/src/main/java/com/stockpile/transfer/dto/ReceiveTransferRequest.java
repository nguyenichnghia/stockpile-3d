package com.stockpile.transfer.dto;

import jakarta.validation.constraints.NotNull;

/** Receives an in-transit transfer into a bin of its destination warehouse. */
public record ReceiveTransferRequest(
		@NotNull Long toBinId) {
}
