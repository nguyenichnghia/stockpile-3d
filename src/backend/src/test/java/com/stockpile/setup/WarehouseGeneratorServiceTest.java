package com.stockpile.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.stockpile.inventory.domain.AccessFace;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.relocation.service.BlockingGraph;
import com.stockpile.relocation.service.LotBox;
import com.stockpile.setup.dto.WarehouseGenerationResult;
import com.stockpile.setup.dto.WarehouseGridSpec;
import com.stockpile.setup.service.WarehouseGeneratorService;

@SpringBootTest
@Testcontainers
@Transactional // roll back between methods so count()==0 holds at the start of each
class WarehouseGeneratorServiceTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired WarehouseGeneratorService generatorService;
	@Autowired LocationRepository locationRepository;

	private static WarehouseGridSpec spec(int zones, int aisles, int racks, int levels, int bins) {
		return new WarehouseGridSpec(zones, aisles, racks, levels, bins,
				new BigDecimal("1.2"), new BigDecimal("1.0"), new BigDecimal("1.5"),
				new BigDecimal("2.0"), AccessFace.SOUTH);
	}

	@Test
	void createsProductOfCountsSlots() {
		WarehouseGenerationResult result = generatorService.generate(spec(2, 3, 4, 2, 3));

		assertThat(result.locationsCreated()).isEqualTo(2 * 3 * 4 * 2 * 3);
		assertThat(locationRepository.count()).isEqualTo(2L * 3 * 4 * 2 * 3);
	}

	@Test
	void refusesWhenWarehouseNotEmpty() {
		generatorService.generate(spec(1, 1, 1, 1, 1));

		assertThatThrownBy(() -> generatorService.generate(spec(1, 1, 1, 1, 1)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("already has locations");
	}

	@Test
	void singleLevelGridHasNoBlocking() {
		// One level => footprints are laid out side by side on the floor, so no
		// slot should block any other (bins/racks/aisles are disjoint in x/y).
		generatorService.generate(spec(2, 2, 3, 1, 2));

		List<LotBox> boxes = locationRepository.findAll().stream()
				.map(WarehouseGeneratorServiceTest::box)
				.toList();

		for (LotBox a : boxes) {
			assertThat(BlockingGraph.blockers(a, boxes))
					.as("slot %d should be blocked by nothing on a single level", a.lotId())
					.isEmpty();
		}
	}

	@Test
	void stackedLevelsBlockTheOnesBelow() {
		// Multiple levels share an (x,y) footprint stacked along z. In this
		// block-stacking model the upper level blocks the lower one (you must
		// move the top to reach the bottom) — see BlockingGraph.blocksOnTop.
		generatorService.generate(spec(1, 1, 1, 2, 1));

		List<LotBox> boxes = locationRepository.findAll().stream()
				.map(WarehouseGeneratorServiceTest::box)
				.sorted((p, q) -> Double.compare(p.zMin(), q.zMin()))
				.toList();
		assertThat(boxes).hasSize(2);
		LotBox lower = boxes.get(0);
		List<LotBox> blockers = BlockingGraph.blockers(lower, boxes);
		assertThat(blockers).hasSize(1);
		assertThat(blockers.get(0).zMin()).isGreaterThan(lower.zMin());
	}

	@Test
	void levelsStackAlongZ() {
		generatorService.generate(spec(1, 1, 1, 3, 1));

		List<Location> byLevel = locationRepository.findAll().stream()
				.sorted((p, q) -> p.getLevel().compareTo(q.getLevel()))
				.toList();
		assertThat(byLevel).hasSize(3);
		assertThat(byLevel.get(0).getZ()).isEqualByComparingTo("0.0");
		assertThat(byLevel.get(1).getZ()).isEqualByComparingTo("1.5");
		assertThat(byLevel.get(2).getZ()).isEqualByComparingTo("3.0");
	}

	private static LotBox box(Location l) {
		return new LotBox(l.getId(), l.getId(), l.getLaneId(), l.getAccessFace(), null,
				l.getX().doubleValue(), l.getY().doubleValue(), l.getZ().doubleValue(),
				l.getW().doubleValue(), l.getD().doubleValue(), l.getH().doubleValue());
	}
}
