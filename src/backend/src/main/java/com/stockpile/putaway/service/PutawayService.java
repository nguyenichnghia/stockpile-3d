package com.stockpile.putaway.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.putaway.dto.PutawaySuggestion;
import com.stockpile.putaway.dto.PutawaySuggestion.ScoredBin;
import com.stockpile.relocation.service.BlockingGraph;
import com.stockpile.relocation.service.LotBox;

import lombok.RequiredArgsConstructor;

/**
 * Putaway Engine (SLAP). Scores empty bins for a new lot and recommends the
 * lowest-cost one (docs/01 §8.2, greedy O(F·k)). Read-only: a proposal only.
 *
 * <p>score(c) = w1·distToDock + w2·blockingPenalty + w3·retrievalMisalignment
 * + w4·fitPenalty. Lower is better.
 */
@Service
@RequiredArgsConstructor
public class PutawayService {

	private final LotRepository lotRepository;
	private final LocationRepository locationRepository;
	private final PlacementRepository placementRepository;
	private final PutawayWeights weights;

	@Transactional(readOnly = true)
	public PutawaySuggestion suggest(long lotId) {
		Lot lot = lotRepository.findById(lotId)
				.orElseThrow(() -> new NotFoundException("Lot " + lotId + " not found"));

		List<Location> empty = locationRepository.findEmpty();
		List<ScoredBin> scored = new ArrayList<>();
		for (Location c : empty) {
			if (!fits(lot, c)) {
				continue; // hard filter: a lot that cannot fit is not a candidate
			}
			scored.add(new ScoredBin(c.getId(), score(lot, c)));
		}
		scored.sort(Comparator.comparingDouble(ScoredBin::score));

		Long best = scored.isEmpty() ? null : scored.get(0).binId();
		return new PutawaySuggestion(lotId, best, scored);
	}

	private double score(Lot lot, Location c) {
		return weights.distToDock() * distToDock(c)
				+ weights.blockingPenalty() * blockingPenalty(lot, c)
				+ weights.retrievalMisalignment() * retrievalMisalignment(lot, c)
				+ weights.fitPenalty() * fitPenalty(lot, c);
	}

	/** Euclidean distance from the dock at the origin (0,0,0). */
	private double distToDock(Location c) {
		double x = c.getX().doubleValue();
		double y = c.getY().doubleValue();
		double z = c.getZ().doubleValue();
		return Math.sqrt(x * x + y * y + z * z);
	}

	/** Penalty if placing the lot here would block, or be blocked by, an existing lot. */
	private double blockingPenalty(Lot lot, Location c) {
		LotBox candidate = boxAt(lot, c);
		List<LotBox> laneLots = placementRepository.findByBin_LaneId(c.getLaneId()).stream()
				.map(PutawayService::toBox)
				.toList();
		boolean creates = laneLots.stream()
				.anyMatch(b -> BlockingGraph.blocks(candidate, b) || BlockingGraph.blocks(b, candidate));
		return creates ? 1.0 : 0.0;
	}

	/**
	 * FEFO/turnover alignment: lots due for retrieval sooner should sit lower
	 * (easier to reach). Penalty grows with height, scaled up for urgent lots.
	 */
	private double retrievalMisalignment(Lot lot, Location c) {
		double height = c.getZ().doubleValue();
		double urgency = lot.getExpiry() != null ? 2.0 : 1.0; // has expiry => FEFO-sensitive
		return height * urgency;
	}

	/** Penalty for wasted space: how much bigger the bin is than the lot. */
	private double fitPenalty(Lot lot, Location c) {
		double binVol = c.getW().doubleValue() * c.getD().doubleValue() * c.getH().doubleValue();
		double lotVol = lot.getW().doubleValue() * lot.getD().doubleValue() * lot.getH().doubleValue();
		return Math.max(0.0, binVol - lotVol);
	}

	/** Hard constraint: the lot's bounding box must fit within the bin. */
	private boolean fits(Lot lot, Location c) {
		return lot.getW().compareTo(c.getW()) <= 0
				&& lot.getD().compareTo(c.getD()) <= 0
				&& lot.getH().compareTo(c.getH()) <= 0;
	}

	/** A hypothetical box for the lot placed at the corner of the bin. */
	private static LotBox boxAt(Lot lot, Location c) {
		return new LotBox(lot.getId(), c.getId(), c.getLaneId(), c.getAccessFace(),
				lot.getPredictedRetrievalAt(),
				c.getX().doubleValue(), c.getY().doubleValue(), c.getZ().doubleValue(),
				lot.getW().doubleValue(), lot.getD().doubleValue(), lot.getH().doubleValue());
	}

	private static LotBox toBox(com.stockpile.inventory.domain.Placement p) {
		Location bin = p.getBin();
		Lot lot = p.getLot();
		return new LotBox(lot.getId(), bin.getId(), bin.getLaneId(), bin.getAccessFace(),
				lot.getPredictedRetrievalAt(),
				p.getX().doubleValue(), p.getY().doubleValue(), p.getZ().doubleValue(),
				lot.getW().doubleValue(), lot.getD().doubleValue(), lot.getH().doubleValue());
	}
}
