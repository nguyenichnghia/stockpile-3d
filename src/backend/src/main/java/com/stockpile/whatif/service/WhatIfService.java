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
import com.stockpile.whatif.dto.WhatIfResult;
import com.stockpile.whatif.dto.WhatIfResult.LayoutMetrics;

import lombok.RequiredArgsConstructor;

/**
 * Layout what-if (ADR-0008): builds a hypothetical grid in memory, re-puts
 * every currently placed lot into it with the same SLAP scorer the putaway
 * engine uses, and measures both layouts with the same blocking rules the
 * scene uses. Pure composition of existing cores — nothing is persisted, no
 * ledger entries, no events.
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
	public WhatIfResult simulate(WarehouseGridSpec spec) {
		if (spec.totalSlots() > MAX_SLOTS) {
			throw new IllegalArgumentException(
					"Simulated grid has " + spec.totalSlots() + " slots; the cap is " + MAX_SLOTS);
		}

		List<Placement> placements = placementRepository.findAll();
		LayoutMetrics current = measureCurrent(placements);

		// The hypothetical layout. Ids are synthetic (the entities are never
		// persisted); LotBox and the scorer need them to tell bins apart.
		List<Location> grid = WarehouseGeneratorService.buildGrid(spec);
		for (int i = 0; i < grid.size(); i++) {
			grid.get(i).setId((long) -(i + 1));
		}

		LayoutMetrics simulated = simulatePutaway(placements, grid);
		return new WhatIfResult(current, simulated);
	}

	/** Metrics of the warehouse as it physically stands. */
	private LayoutMetrics measureCurrent(List<Placement> placements) {
		List<LotBox> boxes = placements.stream().map(WhatIfService::toBox).toList();
		double avgDist = placements.stream()
				.mapToDouble(p -> dist(p.getBin()))
				.average()
				.orElse(0.0);
		long bins = locationRepository.count();
		return new LayoutMetrics(
				bins,
				placements.size(),
				0,
				countBlocked(boxes),
				bins == 0 ? 0.0 : (double) occupiedBins(placements) / bins,
				avgDist);
	}

	/** Greedy SLAP putaway of every lot into the empty hypothetical grid. */
	private LayoutMetrics simulatePutaway(List<Placement> placements, List<Location> grid) {
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
						laneLots.getOrDefault(bin.getLaneId(), List.of()), weights);
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
