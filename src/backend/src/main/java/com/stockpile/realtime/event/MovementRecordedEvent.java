package com.stockpile.realtime.event;

import java.math.BigDecimal;
import java.time.Instant;

import com.stockpile.inventory.domain.MovementType;

/**
 * In-process domain event published by the single write path
 * ({@code MovementService}) after a movement is recorded and the placement
 * projection is updated. Carries only scalars so the {@code inventory} package
 * never depends on {@code realtime.dto} — the realtime layer interprets these
 * facts into a client-facing {@code PlacementDelta}.
 *
 * <p>For PICK/OUTBOUND the lot leaves its bin (placement deleted): {@code toBinId}
 * is null and {@code fromLaneId} identifies where it was. For INBOUND(with bin)/
 * PUTAWAY/RELOCATE the lot ends at {@code toBinId} with pose {@code toX/toY/toZ}
 * (the destination bin corner, per the projection).
 */
public record MovementRecordedEvent(
		Long lotId,
		MovementType type,
		Long warehouseId,
		Long fromBinId,
		String fromLaneId,
		Long toBinId,
		String toLaneId,
		BigDecimal toX,
		BigDecimal toY,
		BigDecimal toZ,
		Instant ts) {
}
