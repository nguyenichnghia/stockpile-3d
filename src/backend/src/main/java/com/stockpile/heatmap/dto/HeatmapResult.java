package com.stockpile.heatmap.dto;

import java.util.List;

/**
 * Per-bin heatmap values for the 3D scene to color the whole warehouse by one
 * metric. {@code value} is normalized to [0, 1] (0 = "cool"/green, 1 =
 * "hot"/red) so the frontend can map any metric onto the same gradient.
 */
public record HeatmapResult(String metric, List<Cell> cells) {

	/** One bin's heat value. */
	public record Cell(long binId, double value) {
	}
}
