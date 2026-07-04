package com.stockpile.relocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.stockpile.inventory.domain.AccessFace;
import com.stockpile.inventory.domain.HandlingType;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.domain.Movement;
import com.stockpile.inventory.domain.MovementType;
import com.stockpile.inventory.domain.Sku;
import com.stockpile.inventory.domain.Warehouse;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.SkuRepository;
import com.stockpile.inventory.repository.WarehouseRepository;
import com.stockpile.inventory.service.MovementService;
import com.stockpile.relocation.dto.RelocationPlan;
import com.stockpile.relocation.service.RelocationService;

@SpringBootTest
@Testcontainers
class RelocationServiceTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired RelocationService relocationService;
	@Autowired MovementService movementService;
	@Autowired SkuRepository skuRepository;
	@Autowired LotRepository lotRepository;
	@Autowired LocationRepository locationRepository;
	@Autowired WarehouseRepository warehouseRepository;

	private Warehouse wh;

	@Test
	void freeLotHasEmptyPlan() {
		Location bin = bin("lane-free", 0, 0, 0);
		Lot lot = putaway(bin);
		RelocationPlan plan = relocationService.plan(lot.getId());
		assertThat(plan.steps()).isEmpty();
	}

	@Test
	void stackedLotsAreRelocatedTopFirst() {
		// Two bins stacked at the same (x,y): bottom z=0, top z=2; plus an empty slot.
		Location bottom = bin("lane-stk", 0, 0, 0);
		Location top = bin("lane-stk", 0, 0, 2);
		bin("lane-stk", 5, 5, 0); // empty temp slot in the same lane

		Lot bottomLot = putaway(bottom);
		Lot topLot = putaway(top);

		RelocationPlan plan = relocationService.plan(bottomLot.getId());

		// The lot on top must be moved to free the bottom one.
		assertThat(plan.steps()).hasSize(1);
		assertThat(plan.steps().get(0).lotId()).isEqualTo(topLot.getId());
		assertThat(plan.steps().get(0).fromBinId()).isEqualTo(top.getId());
	}

	// --- helpers ---

	/** One warehouse per test instance (bin codes stay unique via nanoTime). */
	private Warehouse warehouse() {
		if (wh == null) {
			Warehouse w = new Warehouse();
			w.setCode("WH-" + System.nanoTime());
			w.setName("Test warehouse");
			wh = warehouseRepository.save(w);
		}
		return wh;
	}

	private Sku sku() {
		Sku s = new Sku();
		s.setCode("S-" + System.nanoTime());
		s.setName("t");
		s.setW(BigDecimal.ONE);
		s.setD(BigDecimal.ONE);
		s.setH(BigDecimal.ONE);
		s.setWeight(BigDecimal.ONE);
		s.setHandling(HandlingType.FIFO);
		return skuRepository.save(s);
	}

	private Location bin(String lane, double x, double y, double z) {
		Location l = new Location();
		l.setWarehouse(warehouse());
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

	private Lot putaway(Location bin) {
		Lot lot = new Lot();
		lot.setSku(sku());
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
		return lot;
	}
}
