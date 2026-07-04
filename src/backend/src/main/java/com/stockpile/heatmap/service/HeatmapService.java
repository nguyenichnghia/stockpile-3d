package com.stockpile.heatmap.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * <p>Supports {@code fill} (occupancy), {@code blocking} (how buried a lot is)
 * and {@code expiry} (how close a lot is to its use-by date).
 */
@Service
@RequiredArgsConstructor
public class HeatmapService {

	/**
	 * Blocker count that maps to a fully "hot" cell. A lot with this many (or
	 * more) lots on top of / in front of it is drawn fully red.
	 */
	private static final double BLOCKING_CAP = 3.0;

	/**
	 * How many days out an expiry starts to matter. A lot expiring in this many
	 * days or more is 0.0 (green); on its expiry day (or past it) it is 1.0 (red).
	 */
	private static final double EXPIRY_HORIZON_DAYS = 30.0;

	private final LocationRepository locationRepository;
	private final PlacementRepository placementRepository;

	@Transactional(readOnly = true)
	public HeatmapResult compute(String metric, Long warehouseId) {
		String m = metric == null || metric.isBlank() ? "fill" : metric.trim().toLowerCase();
		return switch (m) {
			case "fill" -> fill(warehouseId);
			case "blocking" -> blocking(warehouseId);
			case "expiry" -> expiry(warehouseId);
			default -> throw new IllegalArgumentException("Unknown heatmap metric: " + metric);
		};
	}

	/** Binary occupancy: a bin holding a lot is 1.0 (hot), an empty bin 0.0. */
	private HeatmapResult fill(Long warehouseId) {
		Set<Long> occupied = placementRepository.findByBin_WarehouseId(warehouseId).stream()
				.map(p -> p.getBin().getId())
				.collect(Collectors.toSet());
		List<Cell> cells = locationRepository.findByWarehouseId(warehouseId).stream()
				.map(l -> new Cell(l.getId(), occupied.contains(l.getId()) ? 1.0 : 0.0))
				.toList();
		return new HeatmapResult("fill", cells);
	}

	/**
	 * How buried each lot is: the number of lots directly blocking it (on-top or
	 * in-front, per {@link BlockingGraph}), normalized by {@link #BLOCKING_CAP}.
	 * Blocking is lane-local, so we reason within each lane. Empty bins are 0.0.
	 */
	private HeatmapResult blocking(Long warehouseId) {
		// Group placed lots by lane so the blocking graph stays lane-local.
		Map<String, List<Placement>> byLane = placementRepository.findByBin_WarehouseId(warehouseId).stream()
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

		List<Cell> cells = locationRepository.findByWarehouseId(warehouseId).stream()
				.map(l -> new Cell(l.getId(), valueByBin.getOrDefault(l.getId(), 0.0)))
				.toList();
		return new HeatmapResult("blocking", cells);
	}

	/**
	 * How close each lot is to its use-by date, over {@link #EXPIRY_HORIZON_DAYS}.
	 * Empty bins and lots without an expiry are 0.0 (green); expired lots are 1.0.
	 */
	private HeatmapResult expiry(Long warehouseId) {
		LocalDate today = LocalDate.now();
		Map<Long, Double> valueByBin = new HashMap<>();
		for (Placement p : placementRepository.findByBin_WarehouseId(warehouseId)) {
			valueByBin.put(p.getBin().getId(), expiryUrgency(p.getLot().getExpiry(), today));
		}
		List<Cell> cells = locationRepository.findByWarehouseId(warehouseId).stream()
				.map(l -> new Cell(l.getId(), valueByBin.getOrDefault(l.getId(), 0.0)))
				.toList();
		return new HeatmapResult("expiry", cells);
	}

	/**
	 * Urgency in [0,1]: 0 when {@code expiry} is null or at least the horizon away,
	 * 1 on the expiry day or past it, linear in between. Pure, so it is testable.
	 */
	static double expiryUrgency(LocalDate expiry, LocalDate today) {
		if (expiry == null) {
			return 0.0;
		}
		long daysLeft = ChronoUnit.DAYS.between(today, expiry);
		double urgency = (EXPIRY_HORIZON_DAYS - daysLeft) / EXPIRY_HORIZON_DAYS;
		return Math.max(0.0, Math.min(1.0, urgency));
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
