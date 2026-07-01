package com.stockpile.heatmap.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.heatmap.dto.HeatmapResult;
import com.stockpile.heatmap.dto.HeatmapResult.Cell;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.domain.Placement;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.relocation.service.BlockingGraph;
import com.stockpile.relocation.service.LotBox;

import lombok.RequiredArgsConstructor;

/**
 * Warehouse heatmap: assigns every bin a value in [0, 1] for one metric, so the
 * 3D scene can color the whole warehouse on a green→red gradient. Read-only.
 *
 * <p>Supports {@code fill} (occupancy) and {@code blocking} (how buried a lot
 * is). The switch leaves room to add {@code expiry} later without changing the
 * endpoint contract.
 */
@Service
@RequiredArgsConstructor
public class HeatmapService {

	/**
	 * Blocker count that maps to a fully "hot" cell. A lot with this many (or
	 * more) lots on top of / in front of it is drawn fully red.
	 */
	private static final double BLOCKING_CAP = 3.0;

	private final LocationRepository locationRepository;
	private final PlacementRepository placementRepository;

	@Transactional(readOnly = true)
	public HeatmapResult compute(String metric) {
		String m = metric == null || metric.isBlank() ? "fill" : metric.trim().toLowerCase();
		return switch (m) {
			case "fill" -> fill();
			case "blocking" -> blocking();
			default -> throw new IllegalArgumentException("Unknown heatmap metric: " + metric);
		};
	}

	/** Binary occupancy: a bin holding a lot is 1.0 (hot), an empty bin 0.0. */
	private HeatmapResult fill() {
		Set<Long> occupied = placementRepository.findAll().stream()
				.map(p -> p.getBin().getId())
				.collect(Collectors.toSet());
		List<Cell> cells = locationRepository.findAll().stream()
				.map(l -> new Cell(l.getId(), occupied.contains(l.getId()) ? 1.0 : 0.0))
				.toList();
		return new HeatmapResult("fill", cells);
	}

	/**
	 * How buried each lot is: the number of lots directly blocking it (on-top or
	 * in-front, per {@link BlockingGraph}), normalized by {@link #BLOCKING_CAP}.
	 * Blocking is lane-local, so we reason within each lane. Empty bins are 0.0.
	 */
	private HeatmapResult blocking() {
		// Group placed lots by lane so the blocking graph stays lane-local.
		Map<String, List<Placement>> byLane = placementRepository.findAll().stream()
				.collect(Collectors.groupingBy(p -> p.getBin().getLaneId()));

		Map<Long, Double> valueByBin = new HashMap<>();
		for (List<Placement> lane : byLane.values()) {
			List<LotBox> boxes = lane.stream().map(HeatmapService::toBox).toList();
			for (int i = 0; i < lane.size(); i++) {
				int blockers = BlockingGraph.blockers(boxes.get(i), boxes).size();
				double value = Math.min(blockers / BLOCKING_CAP, 1.0);
				valueByBin.put(lane.get(i).getBin().getId(), value);
			}
		}

		List<Cell> cells = locationRepository.findAll().stream()
				.map(l -> new Cell(l.getId(), valueByBin.getOrDefault(l.getId(), 0.0)))
				.toList();
		return new HeatmapResult("blocking", cells);
	}

	private static LotBox toBox(Placement p) {
		var bin = p.getBin();
		Lot lot = p.getLot();
		return new LotBox(lot.getId(), bin.getId(), bin.getLaneId(), bin.getAccessFace(),
				lot.getPredictedRetrievalAt(),
				p.getX().doubleValue(), p.getY().doubleValue(), p.getZ().doubleValue(),
				lot.getW().doubleValue(), lot.getD().doubleValue(), lot.getH().doubleValue());
	}
}
