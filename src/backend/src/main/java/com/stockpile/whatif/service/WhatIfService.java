package com.stockpile.whatif.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.domain.Placement;
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.putaway.service.PutawayScorer;
import com.stockpile.putaway.service.PutawayWeights;
import com.stockpile.relocation.service.BlockingGraph;
import com.stockpile.relocation.service.LotBox;
import com.stockpile.setup.service.WarehouseGeneratorService;
import com.stockpile.setup.dto.WarehouseGridSpec;
import com.stockpile.whatif.dto.PutawayWeightsDto;
import com.stockpile.whatif.dto.WhatIfPolicyResult;
import com.stockpile.whatif.dto.WhatIfResult;
import com.stockpile.whatif.dto.WhatIfResult.LayoutMetrics;

import lombok.RequiredArgsConstructor;

/**
 * What-if simulations (ADR-0008): re-put every currently placed lot into a set
 * of empty bins with the same SLAP scorer the putaway engine uses, and measure
 * the result with the same blocking rules the scene uses. Pure composition of
 * existing cores — nothing is persisted, no ledger entries, no events.
 *
 * <p>Two flavors share the {@link #simulatePutaway} core:
 * <ul>
 *   <li><b>Layout</b> ({@link #simulate}) — vary the bins: fill a hypothetical
 *       grid, keeping the (default) weights, to see if a different rack shape
 *       helps.
 *   <li><b>Policy</b> ({@link #simulatePolicy}) — vary the weights: keep the
 *       warehouse's real bins and fill them twice, with baseline vs candidate
 *       weights, to isolate the effect of the SLAP cost trade-offs.
 * </ul>
 *
 * <p>Simulated putaway order is expiry-first (nulls last, id as tiebreaker):
 * deterministic and FEFO-friendly, standing in for the unknowable real arrival
 * order. Complexity is O(lots × bins) like a putaway suggestion per lot —
 * fine at v1's medium-warehouse scale; the spec's slot count is capped.
 */
@Service
@RequiredArgsConstructor
public class WhatIfService {

	/** Hard cap on simulated slots so a typo can't ask for a million-bin grid. */
	private static final long MAX_SLOTS = 100_000;

	private final PlacementRepository placementRepository;
	private final LocationRepository locationRepository;
	private final PutawayWeights weights;

	@Transactional(readOnly = true)
	public WhatIfResult simulate(Long warehouseId, WarehouseGridSpec spec) {
		if (spec.totalSlots() > MAX_SLOTS) {
			throw new IllegalArgumentException(
					"Simulated grid has " + spec.totalSlots() + " slots; the cap is " + MAX_SLOTS);
		}

		List<Placement> placements = placementRepository.findByBin_WarehouseId(warehouseId);
		LayoutMetrics current = measureCurrent(warehouseId, placements);

		// The hypothetical layout. Ids are synthetic (the entities are never
		// persisted); LotBox and the scorer need them to tell bins apart.
		List<Location> grid = WarehouseGeneratorService.buildGrid(spec);
		for (int i = 0; i < grid.size(); i++) {
			grid.get(i).setId((long) -(i + 1));
		}

		LayoutMetrics simulated = simulatePutaway(placements, grid, weights);
		return new WhatIfResult(current, simulated);
	}

	/**
	 * Policy what-if: hold the warehouse's real bins fixed and re-fill them twice
	 * — with the configured baseline weights, then with candidate weights merged
	 * onto that baseline. Any metric difference is the weights' doing. The bins
	 * are cleared for the simulation (the current occupancy is irrelevant); it is
	 * the placement it *would* reach, not where lots physically sit.
	 */
	@Transactional(readOnly = true)
	public WhatIfPolicyResult simulatePolicy(Long warehouseId, PutawayWeightsDto candidateSpec) {
		List<Placement> placements = placementRepository.findByBin_WarehouseId(warehouseId);
		List<Location> bins = locationRepository.findByWarehouseId(warehouseId);
		PutawayWeights candidate = candidateSpec.merge(weights);

		LayoutMetrics baseline = simulatePutaway(placements, bins, weights);
		LayoutMetrics candidateMetrics = simulatePutaway(placements, bins, candidate);
		return new WhatIfPolicyResult(baseline, candidateMetrics, weights, candidate);
	}

	/** Metrics of the warehouse as it physically stands. */
	private LayoutMetrics measureCurrent(Long warehouseId, List<Placement> placements) {
		List<LotBox> boxes = placements.stream().map(WhatIfService::toBox).toList();
		double avgDist = placements.stream()
				.mapToDouble(p -> dist(p.getBin()))
				.average()
				.orElse(0.0);
		long bins = locationRepository.countByWarehouseId(warehouseId);
		return new LayoutMetrics(
				bins,
				placements.size(),
				0,
				countBlocked(boxes),
				bins == 0 ? 0.0 : (double) occupiedBins(placements) / bins,
				avgDist);
	}

	/** Greedy SLAP putaway of every lot into the empty {@code grid} with {@code w}. */
	private static LayoutMetrics simulatePutaway(
			List<Placement> placements, List<Location> grid, PutawayWeights w) {
		List<Lot> lots = placements.stream()
				.map(Placement::getLot)
				.sorted(Comparator
						.comparing(Lot::getExpiry, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(Lot::getId))
				.toList();

		Map<Long, Boolean> occupied = new HashMap<>();
		Map<String, List<LotBox>> laneLots = new HashMap<>();
		List<LotBox> placed = new ArrayList<>();
		long unplaced = 0;
		double distSum = 0;

		for (Lot lot : lots) {
			Location best = null;
			double bestScore = Double.POSITIVE_INFINITY;
			for (Location bin : grid) {
				if (occupied.containsKey(bin.getId()) || !PutawayScorer.fits(lot, bin)) {
					continue;
				}
				double score = PutawayScorer.score(lot, bin,
						laneLots.getOrDefault(bin.getLaneId(), List.of()), w);
				if (score < bestScore) {
					bestScore = score;
					best = bin;
				}
			}
			if (best == null) {
				unplaced++;
				continue;
			}
			occupied.put(best.getId(), true);
			LotBox box = boxAt(lot, best);
			laneLots.computeIfAbsent(best.getLaneId(), k -> new ArrayList<>()).add(box);
			placed.add(box);
			distSum += dist(best);
		}

		return new LayoutMetrics(
				grid.size(),
				placed.size(),
				unplaced,
				countBlocked(placed),
				grid.isEmpty() ? 0.0 : (double) placed.size() / grid.size(),
				placed.isEmpty() ? 0.0 : distSum / placed.size());
	}

	/** Lots with ≥1 blocker; lane-local, same rules the scene and reports use. */
	private static long countBlocked(List<LotBox> boxes) {
		Map<String, List<LotBox>> byLane = new HashMap<>();
		for (LotBox b : boxes) {
			byLane.computeIfAbsent(b.laneId(), k -> new ArrayList<>()).add(b);
		}
		long blocked = 0;
		for (List<LotBox> lane : byLane.values()) {
			for (LotBox box : lane) {
				if (!BlockingGraph.blockers(box, lane).isEmpty()) {
					blocked++;
				}
			}
		}
		return blocked;
	}

	private static long occupiedBins(List<Placement> placements) {
		return placements.stream().map(p -> p.getBin().getId()).distinct().count();
	}

	private static double dist(Location bin) {
		double x = bin.getX().doubleValue();
		double y = bin.getY().doubleValue();
		double z = bin.getZ().doubleValue();
		return Math.sqrt(x * x + y * y + z * z);
	}

	private static LotBox toBox(Placement p) {
		return boxWith(p.getLot(), p.getBin(),
				p.getX().doubleValue(), p.getY().doubleValue(), p.getZ().doubleValue());
	}

	/** The lot dropped at the corner of a (hypothetical) bin — putaway pose v1. */
	private static LotBox boxAt(Lot lot, Location bin) {
		return boxWith(lot, bin,
				bin.getX().doubleValue(), bin.getY().doubleValue(), bin.getZ().doubleValue());
	}

	private static LotBox boxWith(Lot lot, Location bin, double x, double y, double z) {
		return new LotBox(lot.getId(), bin.getId(), bin.getLaneId(), bin.getAccessFace(),
				lot.getPredictedRetrievalAt(), x, y, z,
				lot.getW().doubleValue(), lot.getD().doubleValue(), lot.getH().doubleValue());
	}
}
