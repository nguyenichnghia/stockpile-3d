package com.stockpile.heatmap.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.heatmap.dto.HeatmapResult;
import com.stockpile.heatmap.service.HeatmapService;

import lombok.RequiredArgsConstructor;

/**
 * Heatmap endpoint for the 3D scene: per-bin values for one metric. Read-only —
 * presents warehouse state, never changes it.
 */
@RestController
@RequiredArgsConstructor
public class HeatmapController {

	private final HeatmapService heatmapService;

	@GetMapping("/api/heatmap")
	public HeatmapResult heatmap(
			@RequestParam(defaultValue = "fill") String metric, @RequestParam Long warehouseId) {
		return heatmapService.compute(metric, warehouseId);
	}
}
