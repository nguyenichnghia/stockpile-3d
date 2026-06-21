package com.stockpile.inventory.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Movement;
import com.stockpile.inventory.dto.MovementDto;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.MovementRepository;

import lombok.RequiredArgsConstructor;

/**
 * Records physical movements. Every change to the warehouse goes through here:
 * the movement is appended to the ledger (never updated or deleted) and the
 * placement projection is updated in the same transaction.
 */
@Service
@RequiredArgsConstructor
public class MovementService {

	private final MovementRepository movementRepository;
	private final LotRepository lotRepository;
	private final LocationRepository locationRepository;
	private final PlacementProjectionService projectionService;

	@Transactional
	public Movement record(Movement movement) {
		if (movement.getTs() == null) {
			movement.setTs(Instant.now());
		}
		Movement saved = movementRepository.save(movement);
		projectionService.apply(saved);
		return saved;
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
