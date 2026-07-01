package com.stockpile.relocation.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.stockpile.inventory.domain.AccessFace;
import com.stockpile.relocation.dto.RelocationStep;

/**
 * Pure greedy CRP planner (docs/01 §8.3, ADR-0001), decoupled from JPA/Spring —
 * the same split as {@link BlockingGraph} and {@code PutawayScorer}. The caller
 * loads the lane state and the empty candidate slots; this class only runs the
 * heuristic: repeatedly move the highest-priority blocker to the nearest empty
 * slot that creates no new blocking, until the target is free.
 *
 * <p>Not guaranteed minimal, but fast and explainable.
 */
public final class RelocationPlanner {

	private RelocationPlanner() {
	}

	/** A candidate empty slot the planner may move a blocker into. */
	public record EmptySlot(long binId, String laneId, AccessFace accessFace,
			double x, double y, double z) {
	}

	/**
	 * Steps to free {@code target} within {@code lane}. {@code emptySlots} are the
	 * candidate destinations, in preference order (e.g. same lane first). The
	 * {@code lane} list is simulated in place, so pass a mutable copy.
	 *
	 * @throws IllegalStateException if no valid destination exists, or the loop
	 *         cannot resolve blocking within a safety bound.
	 */
	public static List<RelocationStep> plan(LotBox target, List<LotBox> lane, List<EmptySlot> emptySlots) {
		List<RelocationStep> steps = new ArrayList<>();
		int safety = lane.size(); // at most one move per other lot
		while (safety-- >= 0) {
			List<LotBox> blockers = BlockingGraph.blockers(target, lane);
			if (blockers.isEmpty()) {
				return steps;
			}
			LotBox toMove = pickBlocker(blockers);
			EmptySlot dest = findTempSlot(toMove, lane, emptySlots);
			steps.add(new RelocationStep(toMove.lotId(), toMove.binId(), dest.binId()));
			// Simulate the move so the next iteration sees the updated lane.
			toMove.relocateTo(dest.binId(), dest.laneId(), dest.accessFace(),
					dest.x(), dest.y(), dest.z());
		}
		throw new IllegalStateException("Could not resolve blocking for lot " + target.lotId() + " within bounds");
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

	/** First empty slot that, holding {@code toMove}, creates no new blocking. */
	private static EmptySlot findTempSlot(LotBox toMove, List<LotBox> lane, List<EmptySlot> emptySlots) {
		for (EmptySlot c : emptySlots) {
			if (lane.stream().anyMatch(b -> b.binId() == c.binId())) {
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

	/** A hypothetical copy of {@code src} placed at {@code slot}. */
	private static LotBox boxAt(LotBox src, EmptySlot slot) {
		return new LotBox(src.lotId(), slot.binId(), slot.laneId(), slot.accessFace(),
				src.predictedRetrievalAt(),
				slot.x(), slot.y(), slot.z(),
				src.width(), src.depth(), src.height());
	}
}
