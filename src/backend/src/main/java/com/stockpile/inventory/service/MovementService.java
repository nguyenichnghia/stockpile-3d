package com.stockpile.inventory.service;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Movement;
import com.stockpile.inventory.dto.MovementDto;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.MovementRepository;
import com.stockpile.realtime.event.MovementRecordedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Records physical movements. Every change to the warehouse goes through here:
 * the movement is appended to the ledger (never updated or deleted) and the
 * placement projection is updated in the same transaction.
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
	private final PlacementProjectionService projectionService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public Movement record(Movement movement) {
		if (movement.getTs() == null) {
			movement.setTs(Instant.now());
		}
		Movement saved = movementRepository.save(movement);
		projectionService.apply(saved);
		MovementRecordedEvent event = buildEvent(saved);
		if (event != null) {
			eventPublisher.publishEvent(event);
		}
		return saved;
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
		movement.setFromBin(resolveBin(dto.fromBin()));
		movement.setToBin(resolveBin(dto.toBin()));
		movement.setTs(dto.ts());
		movement.setActor(dto.actor());
		movement.setScanRef(dto.scanRef());
		return record(movement);
	}

	private Location resolveBin(Long id) {
		if (id == null) {
			return null;
		}
		return locationRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Location " + id + " not found"));
	}
}
