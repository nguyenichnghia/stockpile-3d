package com.stockpile.inventory.service;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Movement;
import com.stockpile.inventory.domain.Warehouse;
import com.stockpile.inventory.dto.MovementDto;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.MovementRepository;
import com.stockpile.inventory.repository.WarehouseRepository;
import com.stockpile.realtime.event.MovementRecordedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Records physical movements. Every change to the warehouse goes through here:
 * the movement is appended to the ledger (never updated or deleted) and the
 * placement projection is updated in the same transaction.
 *
 * <p>Every movement happens in exactly one warehouse (ADR-0009): the warehouse
 * is derived from the bins, and a movement whose bins belong to two different
 * warehouses is rejected — v1 has no cross-warehouse transfers.
 *
 * <p>After applying the projection it publishes a {@link MovementRecordedEvent}
 * so the realtime layer can push a delta to the 3D scene. Publishing only —
 * this class has no knowledge of WebSocket/STOMP.
 */
@Service
@RequiredArgsConstructor
public class MovementService {

	private final MovementRepository movementRepository;
	private final LotRepository lotRepository;
	private final LocationRepository locationRepository;
	private final WarehouseRepository warehouseRepository;
	private final PlacementProjectionService projectionService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public Movement record(Movement movement) {
		if (movement.getTs() == null) {
			movement.setTs(Instant.now());
		}
		validateWarehouse(movement);
		enforceScanPolicy(movement);
		Movement saved = movementRepository.save(movement);
		projectionService.apply(saved);
		MovementRecordedEvent event = buildEvent(saved);
		if (event != null) {
			eventPublisher.publishEvent(event);
		}
		return saved;
	}

	/**
	 * Enforces the one-warehouse-per-movement rule: both bins (when present) must
	 * belong to the movement's warehouse. When no warehouse was stated, it is
	 * derived from the bins; a movement with neither bins nor warehouse is invalid.
	 */
	private static void validateWarehouse(Movement m) {
		Warehouse fromWh = m.getFromBin() == null ? null : m.getFromBin().getWarehouse();
		Warehouse toWh = m.getToBin() == null ? null : m.getToBin().getWarehouse();

		if (fromWh != null && toWh != null && !fromWh.getId().equals(toWh.getId())) {
			throw new IllegalArgumentException(
					"Movement crosses warehouses (" + fromWh.getCode() + " -> " + toWh.getCode()
							+ "); cross-warehouse transfers are not supported");
		}

		Warehouse binWh = toWh != null ? toWh : fromWh;
		if (m.getWarehouse() == null) {
			if (binWh == null) {
				throw new IllegalArgumentException(
						"warehouseId is required for a movement with no bins (e.g. INBOUND to staging)");
			}
			m.setWarehouse(binWh);
		} else if (binWh != null && !m.getWarehouse().getId().equals(binWh.getId())) {
			throw new IllegalArgumentException(
					"warehouseId does not match the bins' warehouse (" + binWh.getCode() + ")");
		}
	}

	/**
	 * Server-side scan enforcement (the slice ADR-0007 left room for): when the
	 * movement's warehouse has {@code requireScan} on, the scanRef must be the
	 * lot's own barcode {@code LOT-{id}} — proof the right physical box was
	 * touched. Warehouses with the flag off keep the v1 encourage-and-audit
	 * contract: any scanRef (or none) is recorded as-is. Must run after
	 * {@link #validateWarehouse} so the warehouse is resolved.
	 */
	private static void enforceScanPolicy(Movement m) {
		if (!m.getWarehouse().isRequireScan()) {
			return;
		}
		String scanRef = m.getScanRef();
		if (scanRef == null || scanRef.isBlank()) {
			throw new IllegalArgumentException(
					"Warehouse " + m.getWarehouse().getCode()
							+ " requires scan confirmation: scanRef is required");
		}
		String expected = "LOT-" + m.getLot().getId();
		if (!expected.equalsIgnoreCase(scanRef.trim())) {
			throw new IllegalArgumentException(
					"scanRef \"" + scanRef + "\" does not match the movement's lot (expected "
							+ expected + ")");
		}
	}

	/**
	 * Builds the realtime event from a recorded movement, or {@code null} when the
	 * movement changed no placement (INBOUND to staging with no destination bin).
	 */
	private static MovementRecordedEvent buildEvent(Movement m) {
		Location from = m.getFromBin();
		Location to = m.getToBin();
		if (to == null && from == null) {
			// No placement added or removed (e.g. INBOUND to staging) — nothing to push.
			return null;
		}
		return new MovementRecordedEvent(
				m.getLot().getId(),
				m.getType(),
				m.getWarehouse().getId(),
				from == null ? null : from.getId(),
				from == null ? null : from.getLaneId(),
				to == null ? null : to.getId(),
				to == null ? null : to.getLaneId(),
				to == null ? null : to.getX(),
				to == null ? null : to.getY(),
				to == null ? null : to.getZ(),
				m.getTs());
	}

	/** Resolves entity references from a request DTO, then records the movement. */
	@Transactional
	public Movement record(MovementDto dto) {
		Movement movement = new Movement();
		movement.setLot(lotRepository.findById(dto.lotId())
				.orElseThrow(() -> new NotFoundException("Lot " + dto.lotId() + " not found")));
		movement.setType(dto.type());
		movement.setWarehouse(resolveWarehouse(dto.warehouseId()));
		movement.setFromBin(resolveBin(dto.fromBin()));
		movement.setToBin(resolveBin(dto.toBin()));
		movement.setTs(dto.ts());
		movement.setActor(dto.actor());
		movement.setScanRef(dto.scanRef());
		return record(movement);
	}

	private Warehouse resolveWarehouse(Long id) {
		if (id == null) {
			return null;
		}
		return warehouseRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Warehouse " + id + " not found"));
	}

	private Location resolveBin(Long id) {
		if (id == null) {
			return null;
		}
		return locationRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Location " + id + " not found"));
	}
}
