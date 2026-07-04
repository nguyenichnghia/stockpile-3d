package com.stockpile.relocation.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Placement;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.relocation.dto.RelocationPlan;
import com.stockpile.relocation.dto.RelocationStep;
import com.stockpile.relocation.service.RelocationPlanner.EmptySlot;

import lombok.RequiredArgsConstructor;

/**
 * Relocation Engine (CRP). Given a target lot, computes a greedy sequence of
 * moves to free it (docs/01 §8.3, ADR-0001). Read-only: produces a proposal; it
 * never writes to the ledger.
 *
 * <p>This class handles I/O (loading the lane state and empty candidate slots);
 * the greedy heuristic lives in the pure {@link RelocationPlanner} so it can be
 * unit-tested without a database.
 */
@Service
@RequiredArgsConstructor
public class RelocationService {

	private final PlacementRepository placementRepository;
	private final LocationRepository locationRepository;

	@Transactional(readOnly = true)
	public RelocationPlan plan(long lotId) {
		Placement targetPlacement = placementRepository.findByLotId(lotId)
				.orElseThrow(() -> new NotFoundException("No placement for lot " + lotId));
		Long warehouseId = targetPlacement.getBin().getWarehouse().getId();
		String laneId = targetPlacement.getBin().getLaneId();

		// Build the in-memory lane state (lots currently placed in this lane).
		List<LotBox> lane = new ArrayList<>(
				placementRepository.findByBin_WarehouseIdAndBin_LaneId(warehouseId, laneId).stream()
						.map(RelocationService::toBox).toList());
		LotBox target = lane.stream()
				.filter(b -> b.lotId() == lotId)
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Lot " + lotId + " not in lane " + laneId));

		List<RelocationStep> steps = RelocationPlanner.plan(target, lane, emptySlots(warehouseId, laneId));
		return new RelocationPlan(lotId, steps);
	}

	/**
	 * Empty candidate slots, same-lane first then anywhere in the same warehouse
	 * (ADR-0009: a relocation never moves a lot to another warehouse).
	 */
	private List<EmptySlot> emptySlots(Long warehouseId, String laneId) {
		List<Location> candidates = new ArrayList<>(locationRepository.findEmptyInLane(warehouseId, laneId));
		candidates.addAll(locationRepository.findEmptyInWarehouse(warehouseId));
		return candidates.stream().map(RelocationService::toSlot).toList();
	}

	private static EmptySlot toSlot(Location loc) {
		return new EmptySlot(loc.getId(), loc.getLaneId(), loc.getAccessFace(),
				loc.getX().doubleValue(), loc.getY().doubleValue(), loc.getZ().doubleValue());
	}

	private static LotBox toBox(Placement p) {
		Location bin = p.getBin();
		var lot = p.getLot();
		return new LotBox(
				lot.getId(), bin.getId(), bin.getLaneId(), bin.getAccessFace(),
				lot.getPredictedRetrievalAt(),
				p.getX().doubleValue(), p.getY().doubleValue(), p.getZ().doubleValue(),
				lot.getW().doubleValue(), lot.getD().doubleValue(), lot.getH().doubleValue());
	}
}
