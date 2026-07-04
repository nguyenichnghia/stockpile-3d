package com.stockpile.realtime.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.stockpile.realtime.dto.PlacementDelta;
import com.stockpile.realtime.event.MovementRecordedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Broadcasts placement deltas to the 3D scene over STOMP. Listens for
 * {@link MovementRecordedEvent} and sends a {@link PlacementDelta} to the
 * affected lane topic(s).
 *
 * <p>Uses {@link TransactionalEventListener} with {@code AFTER_COMMIT} so a
 * rolled-back movement never emits a phantom delta — realtime stays a read-side
 * consumer, the ledger remains the sole source of truth (ADR-0003 / ADR-0005).
 */
@Service
@RequiredArgsConstructor
public class PlacementBroadcaster {

	private final SimpMessagingTemplate messaging;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMovementRecorded(MovementRecordedEvent e) {
		boolean removed = e.toBinId() == null; // PICK/OUTBOUND leave no placement
		boolean laneChanged = e.fromLaneId() != null
				&& !e.fromLaneId().equals(e.toLaneId());

		if (removed) {
			// Lot left its bin: tell the origin lane to drop it.
			send(e.warehouseId(), e.fromLaneId(), PlacementDelta.remove(e.lotId(), e.ts()));
			return;
		}

		// Lot now sits at the destination bin: add/replace it there.
		send(e.warehouseId(), e.toLaneId(),
				PlacementDelta.upsert(e.lotId(), e.toBinId(), e.toX(), e.toY(), e.toZ(), e.ts()));

		// Relocate across lanes: the origin lane must also drop the lot, or it
		// would keep showing it. (Same-lane relocate: the UPSERT above suffices.)
		if (laneChanged) {
			send(e.warehouseId(), e.fromLaneId(), PlacementDelta.remove(e.lotId(), e.ts()));
		}
	}

	/**
	 * Lane topics are warehouse-qualified (ADR-0009): two warehouses may use the
	 * same lane id without hearing each other's deltas.
	 */
	private void send(Long warehouseId, String laneId, PlacementDelta delta) {
		if (laneId == null) {
			return;
		}
		messaging.convertAndSend("/topic/warehouse/" + warehouseId + "/lane/" + laneId, delta);
	}
}
