package com.stockpile.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.domain.Movement;
import com.stockpile.inventory.domain.Placement;
import com.stockpile.inventory.repository.MovementRepository;
import com.stockpile.inventory.repository.PlacementRepository;

import lombok.RequiredArgsConstructor;

/**
 * Maintains the {@link Placement} projection from the movement ledger.
 *
 * <p>The ledger is the source of truth; placement is derived. {@link #apply}
 * holds the single state-transition rule used both incrementally (per recorded
 * movement) and during a full {@link #rebuildAll()} replay, so the two paths can
 * never diverge.
 */
@Service
@RequiredArgsConstructor
public class PlacementProjectionService {

	private final PlacementRepository placementRepository;
	private final MovementRepository movementRepository;

	/**
	 * Applies one ledger entry to the placement projection. Pose defaults to the
	 * destination bin's corner coordinates (precise stacking is a later phase).
	 */
	@Transactional
	public void apply(Movement movement) {
		Lot lot = movement.getLot();
		switch (movement.getType()) {
			case INBOUND -> {
				// Lot enters the warehouse. Placed straight into a bin when one is
				// given; a null destination means staging (no placement yet).
				if (movement.getToBin() != null) {
					upsert(lot, movement.getToBin());
				}
			}
			case PUTAWAY -> upsert(lot, requireToBin(movement));
			case RELOCATE -> {
				requirePlacement(lot);
				upsert(lot, requireToBin(movement));
			}
			case PICK, OUTBOUND -> {
				requirePlacement(lot);
				placementRepository.deleteByLotId(lot.getId());
			}
		}
	}

	/** Drops the projection and replays the entire ledger in chronological order. */
	@Transactional
	public void rebuildAll() {
		placementRepository.deleteAllInBatch();
		for (Movement movement : movementRepository.findAllByOrderByTsAscIdAsc()) {
			apply(movement);
		}
	}

	/** Inserts or updates the lot's single placement at the given bin. */
	private void upsert(Lot lot, Location bin) {
		Placement placement = placementRepository.findByLotId(lot.getId())
				.orElseGet(() -> {
					Placement created = new Placement();
					created.setLot(lot);
					return created;
				});
		placement.setBin(bin);
		placement.setX(bin.getX());
		placement.setY(bin.getY());
		placement.setZ(bin.getZ());
		placementRepository.save(placement);
	}

	private static Location requireToBin(Movement movement) {
		Location toBin = movement.getToBin();
		if (toBin == null) {
			throw new IllegalStateException(
					"Movement " + movement.getType() + " requires a destination bin");
		}
		return toBin;
	}

	private void requirePlacement(Lot lot) {
		if (placementRepository.findByLotId(lot.getId()).isEmpty()) {
			throw new IllegalStateException(
					"Lot " + lot.getId() + " has no placement to move or remove");
		}
	}
}
