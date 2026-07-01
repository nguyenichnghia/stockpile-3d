package com.stockpile.heatmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.stockpile.heatmap.dto.HeatmapResult;
import com.stockpile.heatmap.dto.HeatmapResult.Cell;
import com.stockpile.heatmap.service.HeatmapService;
import com.stockpile.inventory.domain.AccessFace;
import com.stockpile.inventory.domain.HandlingType;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.domain.Movement;
import com.stockpile.inventory.domain.MovementType;
import com.stockpile.inventory.domain.Sku;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.SkuRepository;
import com.stockpile.inventory.service.MovementService;

@SpringBootTest
@Testcontainers
@Transactional
class HeatmapServiceTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired HeatmapService heatmapService;
	@Autowired MovementService movementService;
	@Autowired SkuRepository skuRepository;
	@Autowired LotRepository lotRepository;
	@Autowired LocationRepository locationRepository;

	@Test
	void fillIsOneForOccupiedZeroForEmpty() {
		Location occupied = bin(0);
		Location empty = bin(3);
		putaway(occupied);

		Map<Long, Double> byBin = values(heatmapService.compute("fill"));

		assertThat(byBin.get(occupied.getId())).isEqualTo(1.0);
		assertThat(byBin.get(empty.getId())).isEqualTo(0.0);
	}

	@Test
	void fillCoversEveryBin() {
		bin(0);
		bin(3);
		bin(6);

		HeatmapResult result = heatmapService.compute("fill");

		assertThat(result.metric()).isEqualTo("fill");
		assertThat(result.cells()).hasSize(3);
	}

	@Test
	void unknownMetricIsRejected() {
		assertThatThrownBy(() -> heatmapService.compute("nope"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void blockingIsHotForBuriedLotAndZeroForTop() {
		// Two lots stacked in the same lane: bottom (z=0) is blocked by top (z=2).
		Location bottom = binAt("lane-stk", 0, 0, 0);
		Location top = binAt("lane-stk", 0, 0, 2);
		putaway(bottom);
		putaway(top);

		Map<Long, Double> byBin = values(heatmapService.compute("blocking"));

		// bottom has 1 blocker -> 1/CAP(3) > 0; top has none -> 0.
		assertThat(byBin.get(bottom.getId())).isGreaterThan(0.0);
		assertThat(byBin.get(top.getId())).isEqualTo(0.0);
	}

	@Test
	void blockingIsZeroForEmptyBin() {
		Location empty = binAt("lane-e", 5, 5, 0);

		Map<Long, Double> byBin = values(heatmapService.compute("blocking"));

		assertThat(byBin.get(empty.getId())).isEqualTo(0.0);
	}

	@Test
	void expiryIsHotForSoonExpiringAndZeroForEmpty() {
		Location expiring = binAt("lane-x", 0, 0, 0);
		Location empty = binAt("lane-y", 5, 5, 0);
		putawayExpiring(expiring, LocalDate.now()); // expires today -> fully hot

		Map<Long, Double> byBin = values(heatmapService.compute("expiry"));

		assertThat(byBin.get(expiring.getId())).isEqualTo(1.0);
		assertThat(byBin.get(empty.getId())).isEqualTo(0.0);
	}

	private static Map<Long, Double> values(HeatmapResult r) {
		return r.cells().stream().collect(Collectors.toMap(Cell::binId, Cell::value));
	}

	// --- helpers ---

	/** A bin in its own lane at (x,0,0) — for fill tests where lanes don't matter. */
	private Location bin(double x) {
		return binAt("lane-" + System.nanoTime(), x, 0, 0);
	}

	/** A bin in a given lane at (x,y,z) — for blocking tests that stack lots. */
	private Location binAt(String lane, double x, double y, double z) {
		Location l = new Location();
		l.setZone("Z");
		l.setAisle("A");
		l.setRack("R");
		l.setLevel("1");
		l.setBin("B-" + System.nanoTime());
		l.setX(BigDecimal.valueOf(x));
		l.setY(BigDecimal.valueOf(y));
		l.setZ(BigDecimal.valueOf(z));
		l.setW(BigDecimal.ONE);
		l.setD(BigDecimal.ONE);
		l.setH(BigDecimal.ONE);
		l.setLaneId(lane);
		l.setAccessFace(AccessFace.TOP);
		return locationRepository.save(l);
	}

	private void putaway(Location bin) {
		putawayExpiring(bin, null);
	}

	private void putawayExpiring(Location bin, LocalDate expiry) {
		Sku sku = new Sku();
		sku.setCode("S-" + System.nanoTime());
		sku.setName("t");
		sku.setW(BigDecimal.ONE);
		sku.setD(BigDecimal.ONE);
		sku.setH(BigDecimal.ONE);
		sku.setWeight(BigDecimal.ONE);
		sku.setHandling(HandlingType.FIFO);
		sku = skuRepository.save(sku);

		Lot lot = new Lot();
		lot.setSku(sku);
		lot.setW(BigDecimal.ONE);
		lot.setD(BigDecimal.ONE);
		lot.setH(BigDecimal.ONE);
		lot.setWeight(BigDecimal.ONE);
		lot.setExpiry(expiry);
		lot = lotRepository.save(lot);

		Movement m = new Movement();
		m.setLot(lot);
		m.setType(MovementType.PUTAWAY);
		m.setToBin(bin);
		movementService.record(m);
	}
}
