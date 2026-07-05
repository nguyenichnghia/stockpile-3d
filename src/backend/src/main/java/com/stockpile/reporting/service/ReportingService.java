package com.stockpile.reporting.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.domain.Placement;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.MovementRepository;
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.inventory.repository.WarehouseRepository;
import com.stockpile.picking.domain.OrderStatus;
import com.stockpile.picking.repository.PickOrderRepository;
import com.stockpile.relocation.service.BlockingGraph;
import com.stockpile.relocation.service.LotBox;
import com.stockpile.reporting.dto.MovementDaily;
import com.stockpile.reporting.dto.ReportSummary;

import lombok.RequiredArgsConstructor;

/**
 * Management-level aggregates over the warehouse: occupancy, blocking, expiry
 * pressure, order backlog and ledger throughput. Read-only — numbers are
 * derived from the same sources the engines use (placement projection,
 * BlockingGraph, ledger), so the dashboard never disagrees with the scene.
 *
 * <p>All aggregates are per warehouse (ADR-0009). Days follow the warehouse's
 * {@code timezone} (Flyway V5, default UTC): the ledger stores {@link Instant}s
 * and only the bucketing into "today"/daily rows is local.
 */
@Service
@RequiredArgsConstructor
public class ReportingService {

	/** FEFO horizon, same as the heatmap's: expiry within this many days counts. */
	private static final int EXPIRY_HORIZON_DAYS = 30;

	private static final int DEFAULT_DAYS = 14;
	private static final int MAX_DAYS = 90;

	private final LocationRepository locationRepository;
	private final PlacementRepository placementRepository;
	private final MovementRepository movementRepository;
	private final PickOrderRepository pickOrderRepository;
	private final WarehouseRepository warehouseRepository;

	@Transactional(readOnly = true)
	public ReportSummary summary(Long warehouseId) {
		ZoneId zone = zoneOf(warehouseId);
		long totalBins = locationRepository.countByWarehouseId(warehouseId);
		List<Placement> placements = placementRepository.findByBin_WarehouseId(warehouseId);

		long occupiedBins = placements.stream()
				.map(p -> p.getBin().getId())
				.distinct()
				.count();

		LocalDate today = LocalDate.now(zone);
		LocalDate horizon = today.plusDays(EXPIRY_HORIZON_DAYS);
		long expired = placements.stream()
				.filter(p -> isBefore(p.getLot(), today))
				.count();
		long expiringSoon = placements.stream()
				.filter(p -> {
					LocalDate expiry = p.getLot().getExpiry();
					return expiry != null && !expiry.isBefore(today) && !expiry.isAfter(horizon);
				})
				.count();

		return new ReportSummary(
				totalBins,
				occupiedBins,
				totalBins == 0 ? 0.0 : (double) occupiedBins / totalBins,
				placements.size(),
				countBlocked(placements),
				expiringSoon,
				expired,
				pickOrderRepository.countByWarehouseIdAndStatus(warehouseId, OrderStatus.OPEN),
				movementRepository.countByWarehouseIdAndTsGreaterThanEqual(
						warehouseId, today.atStartOfDay(zone).toInstant()),
				zone.getId());
	}

	/**
	 * Ledger throughput per (local day, type) for the last {@code days} days,
	 * today included. Null falls back to {@value #DEFAULT_DAYS}; capped at
	 * {@value #MAX_DAYS} to keep the scan bounded.
	 */
	@Transactional(readOnly = true)
	public List<MovementDaily> movementsDaily(Integer days, Long warehouseId) {
		int d = days == null ? DEFAULT_DAYS : days;
		if (d < 1 || d > MAX_DAYS) {
			throw new IllegalArgumentException("days must be between 1 and " + MAX_DAYS);
		}
		ZoneId zone = zoneOf(warehouseId);
		Instant from = LocalDate.now(zone)
				.minusDays(d - 1L)
				.atStartOfDay(zone)
				.toInstant();
		return movementRepository.countDailyByType(warehouseId, from, zone.getId()).stream()
				.map(r -> new MovementDaily(r.getDay(), r.getType(), r.getCnt()))
				.toList();
	}

	/**
	 * The warehouse's reporting zone. The stored id was validated on write
	 * ({@code WarehouseService}), so {@code ZoneId.of} cannot fail here.
	 */
	private ZoneId zoneOf(Long warehouseId) {
		return warehouseRepository.findById(warehouseId)
				.map(w -> ZoneId.of(w.getTimezone()))
				.orElseThrow(() -> new NotFoundException("Warehouse " + warehouseId + " not found"));
	}

	/** Placed lots with at least one blocker; lane-local like everything blocking. */
	private static long countBlocked(List<Placement> placements) {
		Map<String, List<Placement>> byLane = placements.stream()
				.collect(Collectors.groupingBy(p -> p.getBin().getLaneId()));

		long blocked = 0;
		for (List<Placement> lane : byLane.values()) {
			List<LotBox> boxes = lane.stream().map(ReportingService::toBox).toList();
			for (LotBox box : boxes) {
				if (!BlockingGraph.blockers(box, boxes).isEmpty()) {
					blocked++;
				}
			}
		}
		return blocked;
	}

	private static boolean isBefore(Lot lot, LocalDate day) {
		return lot.getExpiry() != null && lot.getExpiry().isBefore(day);
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
