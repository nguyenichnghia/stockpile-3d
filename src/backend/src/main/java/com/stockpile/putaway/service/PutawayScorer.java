package com.stockpile.putaway.service;

import java.util.List;

import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.relocation.service.BlockingGraph;
import com.stockpile.relocation.service.LotBox;

/**
 * Pure SLAP scoring (docs/01 §8.2), decoupled from JPA/Spring so it is fast and
 * trivial to unit-test — the same split as {@link BlockingGraph}. The caller
 * loads the candidate bins and the lane's existing lots; this class only does
 * the math.
 *
 * <p>score(c) = w1·distToDock + w2·blockingPenalty + w3·retrievalMisalignment
 * + w4·fitPenalty. Lower is better.
 */
public final class PutawayScorer {

	private PutawayScorer() {
	}

	/** Total cost of placing {@code lot} in {@code bin}; {@code laneLots} are the
	 *  lots already in that bin's lane. Lower is better. */
	public static double score(Lot lot, Location bin, List<LotBox> laneLots, PutawayWeights w) {
		return w.distToDock() * distToDock(bin)
				+ w.blockingPenalty() * blockingPenalty(lot, bin, laneLots)
				+ w.retrievalMisalignment() * retrievalMisalignment(lot, bin)
				+ w.fitPenalty() * fitPenalty(lot, bin);
	}

	/** Hard constraint: the lot's bounding box must fit within the bin. */
	public static boolean fits(Lot lot, Location bin) {
		return lot.getW().compareTo(bin.getW()) <= 0
				&& lot.getD().compareTo(bin.getD()) <= 0
				&& lot.getH().compareTo(bin.getH()) <= 0;
	}

	/** Euclidean distance from the dock at the origin (0,0,0). */
	static double distToDock(Location c) {
		double x = c.getX().doubleValue();
		double y = c.getY().doubleValue();
		double z = c.getZ().doubleValue();
		return Math.sqrt(x * x + y * y + z * z);
	}

	/** Penalty if placing the lot here would block, or be blocked by, an existing lot. */
	static double blockingPenalty(Lot lot, Location c, List<LotBox> laneLots) {
		LotBox candidate = boxAt(lot, c);
		boolean creates = laneLots.stream()
				.anyMatch(b -> BlockingGraph.blocks(candidate, b) || BlockingGraph.blocks(b, candidate));
		return creates ? 1.0 : 0.0;
	}

	/**
	 * FEFO/turnover alignment: lots due for retrieval sooner should sit lower
	 * (easier to reach). Penalty grows with height, scaled up for urgent lots.
	 */
	static double retrievalMisalignment(Lot lot, Location c) {
		double height = c.getZ().doubleValue();
		double urgency = lot.getExpiry() != null ? 2.0 : 1.0; // has expiry => FEFO-sensitive
		return height * urgency;
	}

	/** Penalty for wasted space: how much bigger the bin is than the lot. */
	static double fitPenalty(Lot lot, Location c) {
		double binVol = c.getW().doubleValue() * c.getD().doubleValue() * c.getH().doubleValue();
		double lotVol = lot.getW().doubleValue() * lot.getD().doubleValue() * lot.getH().doubleValue();
		return Math.max(0.0, binVol - lotVol);
	}

	/** A hypothetical box for the lot placed at the corner of the bin. */
	private static LotBox boxAt(Lot lot, Location c) {
		return new LotBox(lot.getId(), c.getId(), c.getLaneId(), c.getAccessFace(),
				lot.getPredictedRetrievalAt(),
				c.getX().doubleValue(), c.getY().doubleValue(), c.getZ().doubleValue(),
				lot.getW().doubleValue(), lot.getD().doubleValue(), lot.getH().doubleValue());
	}
}
