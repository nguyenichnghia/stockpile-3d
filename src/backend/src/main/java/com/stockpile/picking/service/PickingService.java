package com.stockpile.picking.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.inventory.domain.AccessFace;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Placement;
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.picking.domain.OrderLine;
import com.stockpile.picking.domain.PickOrder;
import com.stockpile.picking.dto.PickLineResult;
import com.stockpile.picking.dto.PickPlan;
import com.stockpile.picking.dto.PickStep;
import com.stockpile.picking.service.PickPlanner.Candidate;
import com.stockpile.relocation.dto.RelocationPlan;
import com.stockpile.relocation.dto.RelocationStep;
import com.stockpile.relocation.service.BlockingGraph;
import com.stockpile.relocation.service.LotBox;
import com.stockpile.relocation.service.RelocationService;

import lombok.RequiredArgsConstructor;

/**
 * Picking Engine (docs/01 §8.4, ADR-0006). Turns an order into a proposed
 * pick-list: for each line it selects lots (FEFO/FIFO, then least-blocked via
 * {@link PickPlanner}) and, for any blocked lot, prepends the relocation steps
 * needed to free it (reusing {@link RelocationService} — the CRP engine). Purely
 * a proposal: it never writes to the ledger.
 *
 * <p>I/O lives here; the ordering decision is the pure {@link PickPlanner}, and
 * blocking is the pure {@link BlockingGraph} — so both are unit-testable without
 * a database.
 */
@Service
@RequiredArgsConstructor
public class PickingService {

	private final OrderService orderService;
	private final PlacementRepository placementRepository;
	private final RelocationService relocationService;

	@Transactional(readOnly = true)
	public PickPlan plan(long orderId) {
		PickOrder order = orderService.get(orderId);
		Long warehouseId = order.getWarehouse().getId();

		List<PickLineResult> lineResults = new ArrayList<>();
		List<PickStep> steps = new ArrayList<>();
		// A lot freed for one pick stays freed for later picks in the same plan,
		// so we don't re-plan a relocation for a bin we've already cleared.
		Map<Long, Integer> blockersByLot = new HashMap<>();

		for (OrderLine line : order.getLines()) {
			String skuCode = line.getSku().getCode();
			// Only stock in the order's warehouse is eligible (ADR-0009).
			List<Placement> placements = placementRepository
					.findByBin_WarehouseIdAndLot_Sku_CodeIgnoreCase(warehouseId, skuCode);

			List<Candidate> candidates = toCandidates(warehouseId, placements, blockersByLot);
			List<Candidate> chosen = PickPlanner.select(candidates, line.getSku().getHandling(), line.getQty());

			for (Candidate c : chosen) {
				appendPickWithRelocations(c, steps);
			}
			lineResults.add(PickLineResult.of(skuCode, line.getQty(), chosen.size()));
		}
		return new PickPlan(orderId, lineResults, steps);
	}

	/** Emits the relocation steps to free a chosen lot (if blocked), then its pick. */
	private void appendPickWithRelocations(Candidate c, List<PickStep> steps) {
		if (c.blockers() > 0) {
			RelocationPlan plan = relocationService.plan(c.lotId());
			for (RelocationStep r : plan.steps()) {
				steps.add(PickStep.relocate(r.lotId(), r.fromBinId(), r.toBinId()));
			}
		}
		steps.add(PickStep.pick(c.lotId(), c.binId()));
	}

	/**
	 * Turns placements of a SKU into planner candidates, computing each lot's
	 * blocker count from its lane. {@code blockersByLot} caches per-lane work
	 * across lines that share lanes.
	 */
	private List<Candidate> toCandidates(Long warehouseId, List<Placement> placements,
			Map<Long, Integer> blockersByLot) {
		List<Candidate> candidates = new ArrayList<>();
		for (Placement p : placements) {
			int blockers = blockersByLot.computeIfAbsent(
					p.getLot().getId(), lotId -> countBlockers(lotId, warehouseId, p.getBin().getLaneId()));
			candidates.add(new Candidate(
					p.getLot().getId(),
					p.getBin().getId(),
					p.getLot().getExpiry(),
					p.getLot().getPredictedRetrievalAt(),
					blockers));
		}
		return candidates;
	}

	/** How many lots block the given lot within its lane (pure BlockingGraph). */
	private int countBlockers(long lotId, Long warehouseId, String laneId) {
		List<LotBox> lane = placementRepository.findByBin_WarehouseIdAndBin_LaneId(warehouseId, laneId).stream()
				.map(PickingService::toBox)
				.toList();
		LotBox target = lane.stream().filter(b -> b.lotId() == lotId).findFirst().orElse(null);
		return target == null ? 0 : BlockingGraph.blockers(target, lane).size();
	}

	private static LotBox toBox(Placement p) {
		Location bin = p.getBin();
		var lot = p.getLot();
		AccessFace face = bin.getAccessFace();
		return new LotBox(
				lot.getId(), bin.getId(), bin.getLaneId(), face,
				lot.getPredictedRetrievalAt(),
				p.getX().doubleValue(), p.getY().doubleValue(), p.getZ().doubleValue(),
				lot.getW().doubleValue(), lot.getD().doubleValue(), lot.getH().doubleValue());
	}
}
