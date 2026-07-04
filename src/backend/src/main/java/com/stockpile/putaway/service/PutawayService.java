package com.stockpile.putaway.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.domain.Placement;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.putaway.dto.PutawaySuggestion;
import com.stockpile.putaway.dto.PutawaySuggestion.ScoredBin;
import com.stockpile.relocation.service.LotBox;

import lombok.RequiredArgsConstructor;

/**
 * Putaway Engine (SLAP). Scores empty bins for a new lot and recommends the
 * lowest-cost one (docs/01 §8.2, greedy O(F·k)). Read-only: a proposal only.
 *
 * <p>This class handles I/O (loading candidates and lane state); the scoring
 * math lives in the pure {@link PutawayScorer} so it can be unit-tested without
 * a database.
 */
@Service
@RequiredArgsConstructor
public class PutawayService {

	private final LotRepository lotRepository;
	private final LocationRepository locationRepository;
	private final PlacementRepository placementRepository;
	private final PutawayWeights weights;

	@Transactional(readOnly = true)
	public PutawaySuggestion suggest(long lotId, long warehouseId) {
		Lot lot = lotRepository.findById(lotId)
				.orElseThrow(() -> new NotFoundException("Lot " + lotId + " not found"));

		List<Location> empty = locationRepository.findEmptyInWarehouse(warehouseId);
		List<ScoredBin> scored = new ArrayList<>();
		for (Location c : empty) {
			if (!PutawayScorer.fits(lot, c)) {
				continue; // hard filter: a lot that cannot fit is not a candidate
			}
			scored.add(new ScoredBin(c.getId(), PutawayScorer.score(lot, c, laneLots(c), weights)));
		}
		scored.sort(Comparator.comparingDouble(ScoredBin::score));

		Long best = scored.isEmpty() ? null : scored.get(0).binId();
		return new PutawaySuggestion(lotId, best, scored);
	}

	/** Existing lots in the bin's lane, as boxes for the blocking check. */
	private List<LotBox> laneLots(Location bin) {
		return placementRepository
				.findByBin_WarehouseIdAndBin_LaneId(bin.getWarehouse().getId(), bin.getLaneId()).stream()
				.map(PutawayService::toBox)
				.toList();
	}

	private static LotBox toBox(Placement p) {
		Location bin = p.getBin();
		Lot lot = p.getLot();
		return new LotBox(lot.getId(), bin.getId(), bin.getLaneId(), bin.getAccessFace(),
				lot.getPredictedRetrievalAt(),
				p.getX().doubleValue(), p.getY().doubleValue(), p.getZ().doubleValue(),
				lot.getW().doubleValue(), lot.getD().doubleValue(), lot.getH().doubleValue());
	}
}
