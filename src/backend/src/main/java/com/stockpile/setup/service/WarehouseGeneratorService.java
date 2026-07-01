package com.stockpile.setup.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.setup.dto.WarehouseGenerationResult;
import com.stockpile.setup.dto.WarehouseGridSpec;

import lombok.RequiredArgsConstructor;

/**
 * Generates a regular grid warehouse from a {@link WarehouseGridSpec}
 * (docs/warehouse-setup.md §2). Creates {@code location} rows only — never
 * {@code movement}/{@code placement}; putting stock in still goes through the
 * ledger (ADR-0003).
 *
 * <p>Coordinate layout (warehouse axes: x = width, y = depth, z = height):
 * <ul>
 *   <li>bins in a level run along <b>x</b> (side by side),</li>
 *   <li>racks continue further along <b>x</b> after each rack's bins,</li>
 *   <li>levels stack along <b>z</b>,</li>
 *   <li>aisles are separated along <b>y</b> by one bin-depth plus {@code aisleGap},</li>
 *   <li>zones are blocks of aisles, offset further along <b>y</b>.</li>
 * </ul>
 * This keeps every slot's footprint disjoint, so the generated warehouse has no
 * spurious blocking. Each {@code (zone, aisle, rack)} shares one lane id.
 */
@Service
@RequiredArgsConstructor
public class WarehouseGeneratorService {

	private final LocationRepository locationRepository;

	@Transactional
	public WarehouseGenerationResult generate(WarehouseGridSpec spec) {
		if (locationRepository.count() > 0) {
			throw new IllegalStateException(
					"Warehouse already has locations; generation is only allowed on an empty warehouse");
		}

		BigDecimal binW = spec.binWidth();
		BigDecimal binD = spec.binDepth();
		BigDecimal binH = spec.binHeight();
		// One aisle's depth (a single rack row) plus the walkway to the next aisle.
		BigDecimal aisleStride = binD.add(spec.aisleGap());

		List<Location> batch = new ArrayList<>();
		for (int z = 1; z <= spec.zones(); z++) {
			for (int a = 1; a <= spec.aislesPerZone(); a++) {
				// Global aisle index across all zones gives a unique depth offset,
				// so zones are stacked blocks of aisles along y (no overlap).
				int globalAisle = (z - 1) * spec.aislesPerZone() + (a - 1);
				BigDecimal yPos = aisleStride.multiply(BigDecimal.valueOf(globalAisle));
				String laneBase = code(z) + "-" + code(a);

				for (int r = 1; r <= spec.racksPerAisle(); r++) {
					String laneId = laneBase + "-" + code(r);
					for (int lvl = 1; lvl <= spec.levelsPerRack(); lvl++) {
						BigDecimal zPos = binH.multiply(BigDecimal.valueOf(lvl - 1));
						for (int b = 1; b <= spec.binsPerLevel(); b++) {
							// x runs over racks first, then bins within a rack, so
							// racks do not overlap: rackOffset = (r-1)*binsPerLevel.
							int xIndex = (r - 1) * spec.binsPerLevel() + (b - 1);
							BigDecimal xPos = binW.multiply(BigDecimal.valueOf(xIndex));
							batch.add(slot(spec, laneId, z, a, r, lvl, b, xPos, yPos, zPos));
						}
					}
				}
			}
		}

		List<Location> saved = locationRepository.saveAll(batch);
		return new WarehouseGenerationResult(saved.size(), spec.zones(), spec.aislesPerZone(),
				spec.racksPerAisle(), spec.levelsPerRack(), spec.binsPerLevel());
	}

	private static Location slot(WarehouseGridSpec spec, String laneId,
			int zone, int aisle, int rack, int level, int bin,
			BigDecimal x, BigDecimal y, BigDecimal z) {
		Location loc = new Location();
		loc.setZone(code(zone));
		loc.setAisle(code(aisle));
		loc.setRack(code(rack));
		loc.setLevel(code(level));
		loc.setBin(code(bin));
		loc.setX(x);
		loc.setY(y);
		loc.setZ(z);
		loc.setW(spec.binWidth());
		loc.setD(spec.binDepth());
		loc.setH(spec.binHeight());
		loc.setLaneId(laneId);
		loc.setAccessFace(spec.accessFace());
		return loc;
	}

	/** Zero-padded 2-digit code, e.g. 1 -> "01" (matches typical warehouse labels). */
	private static String code(int n) {
		return String.format("%02d", n);
	}
}
