package com.stockpile.heatmap.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.heatmap.dto.HeatmapResult;
import com.stockpile.heatmap.dto.HeatmapResult.Cell;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.PlacementRepository;

import lombok.RequiredArgsConstructor;

/**
 * Warehouse heatmap: assigns every bin a value in [0, 1] for one metric, so the
 * 3D scene can color the whole warehouse on a green→red gradient. Read-only.
 *
 * <p>v1 supports the {@code fill} metric (occupancy). The switch leaves room to
 * add {@code blocking} (reusing BlockingGraph) and {@code expiry} later without
 * changing the endpoint contract.
 */
@Service
@RequiredArgsConstructor
public class HeatmapService {

	private final LocationRepository locationRepository;
	private final PlacementRepository placementRepository;

	@Transactional(readOnly = true)
	public HeatmapResult compute(String metric) {
		String m = metric == null || metric.isBlank() ? "fill" : metric.trim().toLowerCase();
		return switch (m) {
			case "fill" -> fill();
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
}
