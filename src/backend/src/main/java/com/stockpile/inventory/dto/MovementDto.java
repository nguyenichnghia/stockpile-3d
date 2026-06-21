package com.stockpile.inventory.dto;

import java.time.Instant;

import com.stockpile.inventory.domain.Movement;
import com.stockpile.inventory.domain.MovementType;

import jakarta.validation.constraints.NotNull;

/**
 * Request/response for a ledger entry. The ledger is append-only: this is only
 * ever created (POST), never updated or deleted.
 */
public record MovementDto(
		Long id,
		@NotNull Long lotId,
		@NotNull MovementType type,
		Long fromBin,
		Long toBin,
		Instant ts,
		String actor,
		String scanRef) {

	public static MovementDto from(Movement m) {
		return new MovementDto(
				m.getId(),
				m.getLot().getId(),
				m.getType(),
				m.getFromBin() == null ? null : m.getFromBin().getId(),
				m.getToBin() == null ? null : m.getToBin().getId(),
				m.getTs(),
				m.getActor(),
				m.getScanRef());
	}
}
