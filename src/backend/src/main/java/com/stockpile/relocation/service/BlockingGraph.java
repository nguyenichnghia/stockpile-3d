package com.stockpile.relocation.service;

import java.util.List;

/**
 * Computes the "blocking" relationship between lots — the heart of the system.
 *
 * <p>Lot B blocks lot A if freeing A requires moving B first. Two cases (docs/01 §6):
 * <ul>
 *   <li><b>on-top</b>: B rests on A ({@code B.zMin >= A.zMax}) with overlapping
 *       (x,y) footprint — flush stacking counts. Two lots at the same level still
 *       do not block (their z-ranges overlap, and the footprint check separates
 *       "stacked" from "side by side"). See Dev Log 2026-06-22.</li>
 *   <li><b>in-front</b>: same lane, B is nearer the access face than A and covers
 *       A's exit path (overlap on the two axes orthogonal to the retrieval axis).</li>
 * </ul>
 *
 * <p>Pure logic, no JPA/Spring, so it is fast and trivial to unit-test.
 */
public final class BlockingGraph {

	private BlockingGraph() {
	}

	/** Returns the lots in {@code others} that directly block {@code target}. */
	public static List<LotBox> blockers(LotBox target, List<LotBox> others) {
		return others.stream()
				.filter(b -> b.lotId() != target.lotId())
				.filter(b -> blocks(b, target))
				.toList();
	}

	/** True if {@code b} blocks {@code a} (on-top or in-front). */
	public static boolean blocks(LotBox b, LotBox a) {
		return blocksOnTop(b, a) || blocksInFront(b, a);
	}

	private static boolean blocksOnTop(LotBox b, LotBox a) {
		// b is on top of a when its bottom is at or above a's top AND their (x,y)
		// footprints overlap. Using '>=' counts flush stacking (b.zMin == a.zMax)
		// as blocking — physically b rests on a. Two lots at the same level still
		// do NOT block: there z-ranges overlap so neither zMin reaches the other's
		// zMax (Dev Log 2026-06-25 / 2026-06-22). The (x,y) overlap check is what
		// distinguishes "stacked" from "side by side".
		return b.zMin() >= a.zMax() && overlaps(b.xMin(), b.xMax(), a.xMin(), a.xMax())
				&& overlaps(b.yMin(), b.yMax(), a.yMin(), a.yMax());
	}

	private static boolean blocksInFront(LotBox b, LotBox a) {
		if (a.accessFace() == null || a.accessFace() != b.accessFace()) {
			return false;
		}
		if (a.laneId() == null || !a.laneId().equals(b.laneId())) {
			return false;
		}
		return switch (a.accessFace()) {
			// Retrieval along the depth (y) axis: nearer-the-face means smaller/larger y.
			case NORTH -> b.yMin() > a.yMax()
					&& overlaps(b.xMin(), b.xMax(), a.xMin(), a.xMax())
					&& overlaps(b.zMin(), b.zMax(), a.zMin(), a.zMax());
			case SOUTH -> b.yMax() < a.yMin()
					&& overlaps(b.xMin(), b.xMax(), a.xMin(), a.xMax())
					&& overlaps(b.zMin(), b.zMax(), a.zMin(), a.zMax());
			// Retrieval along the width (x) axis.
			case EAST -> b.xMin() > a.xMax()
					&& overlaps(b.yMin(), b.yMax(), a.yMin(), a.yMax())
					&& overlaps(b.zMin(), b.zMax(), a.zMin(), a.zMax());
			case WEST -> b.xMax() < a.xMin()
					&& overlaps(b.yMin(), b.yMax(), a.yMin(), a.yMax())
					&& overlaps(b.zMin(), b.zMax(), a.zMin(), a.zMax());
			// Retrieval from the top is covered by the on-top rule.
			case TOP -> false;
		};
	}

	/** Half-open interval overlap on one axis (touching edges do not overlap). */
	private static boolean overlaps(double min1, double max1, double min2, double max2) {
		return min1 < max2 && min2 < max1;
	}
}
