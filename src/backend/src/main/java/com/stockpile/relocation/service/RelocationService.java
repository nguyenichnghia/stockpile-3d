package com.stockpile.relocation.service;

import java.util.ArrayList;
import java.util.Comparator;
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

import lombok.RequiredArgsConstructor;

/**
 * Relocation Engine (CRP). Given a target lot, computes a greedy sequence of
 * moves to free it. Greedy heuristic (docs/01 §8.3, ADR-0001): repeatedly move
 * the highest-priority blocker to the nearest temp slot that creates no new
 * blocking. Not guaranteed minimal, but fast and explainable.
 *
 * <p>Read-only: produces a proposal; it never writes to the ledger.
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
		String laneId = targetPlacement.getBin().getLaneId();

		// Build the in-memory lane state (lots currently placed in this lane).
		List<LotBox> lane = new ArrayList<>(
				placementRepository.findByBin_LaneId(laneId).stream().map(RelocationService::toBox).toList());
		LotBox target = lane.stream()
				.filter(b -> b.lotId() == lotId)
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Lot " + lotId + " not in lane " + laneId));

		List<RelocationStep> steps = new ArrayList<>();
		int safety = lane.size(); // at most one move per other lot
		while (safety-- >= 0) {
			List<LotBox> blockers = BlockingGraph.blockers(target, lane);
			if (blockers.isEmpty()) {
				return new RelocationPlan(lotId, steps);
			}
			LotBox toMove = pickBlocker(blockers);
			Location dest = findTempSlot(toMove, lane, laneId);
			steps.add(new RelocationStep(toMove.lotId(), toMove.binId(), dest.getId()));
			// Simulate the move so the next iteration sees the updated lane.
			applyMove(toMove, dest);
		}
		throw new IllegalStateException("Could not resolve blocking for lot " + lotId + " within bounds");
	}

	/** Greedy choice: move the blocker retrieved latest, breaking ties by height. */
	private static LotBox pickBlocker(List<LotBox> blockers) {
		return blockers.stream()
				.max(Comparator
						.comparing((LotBox b) -> b.predictedRetrievalAt() == null
								? Long.MAX_VALUE
								: b.predictedRetrievalAt().toEpochMilli())
						.thenComparingDouble(LotBox::zMin))
				.orElseThrow();
	}

	/** Nearest empty slot (same lane first, then anywhere) that creates no new blocking. */
	private Location findTempSlot(LotBox toMove, List<LotBox> lane, String laneId) {
		List<Location> candidates = new ArrayList<>(locationRepository.findEmptyInLane(laneId));
		candidates.addAll(locationRepository.findEmpty());
		for (Location c : candidates) {
			if (lane.stream().anyMatch(b -> b.binId() == c.getId())) {
				continue; // already occupied in our simulation
			}
			LotBox moved = boxAt(toMove, c);
			boolean createsBlocking = lane.stream()
					.filter(b -> b.lotId() != toMove.lotId())
					.anyMatch(b -> BlockingGraph.blocks(moved, b) || BlockingGraph.blocks(b, moved));
			if (!createsBlocking) {
				return c;
			}
		}
		throw new IllegalStateException("No valid temp location for lot " + toMove.lotId());
	}

	private void applyMove(LotBox box, Location dest) {
		box.relocateTo(dest.getId(), dest.getLaneId(), dest.getAccessFace(),
				dest.getX().doubleValue(), dest.getY().doubleValue(), dest.getZ().doubleValue());
	}

	/** A hypothetical copy of {@code src} placed at the corner of {@code loc}. */
	private static LotBox boxAt(LotBox src, Location loc) {
		LotBox b = new LotBox(src.lotId(), loc.getId(), loc.getLaneId(), loc.getAccessFace(),
				src.predictedRetrievalAt(),
				loc.getX().doubleValue(), loc.getY().doubleValue(), loc.getZ().doubleValue(),
				src.width(), src.depth(), src.height());
		return b;
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
