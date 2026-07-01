package com.stockpile.heatmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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

	private static Map<Long, Double> values(HeatmapResult r) {
		return r.cells().stream().collect(Collectors.toMap(Cell::binId, Cell::value));
	}

	// --- helpers ---

	private Location bin(double x) {
		Location l = new Location();
		l.setZone("Z");
		l.setAisle("A");
		l.setRack("R");
		l.setLevel("1");
		l.setBin("B-" + System.nanoTime());
		l.setX(BigDecimal.valueOf(x));
		l.setY(BigDecimal.ZERO);
		l.setZ(BigDecimal.ZERO);
		l.setW(BigDecimal.ONE);
		l.setD(BigDecimal.ONE);
		l.setH(BigDecimal.ONE);
		l.setLaneId("lane-" + System.nanoTime());
		l.setAccessFace(AccessFace.TOP);
		return locationRepository.save(l);
	}

	private void putaway(Location bin) {
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
		lot = lotRepository.save(lot);

		Movement m = new Movement();
		m.setLot(lot);
		m.setType(MovementType.PUTAWAY);
		m.setToBin(bin);
		movementService.record(m);
	}
}
