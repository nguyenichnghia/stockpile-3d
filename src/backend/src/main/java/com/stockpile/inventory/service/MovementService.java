package com.stockpile.inventory.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.inventory.domain.Movement;
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
}
