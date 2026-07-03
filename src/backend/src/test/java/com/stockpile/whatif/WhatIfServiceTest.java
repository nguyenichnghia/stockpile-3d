package com.stockpile.whatif;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
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
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.SkuRepository;
import com.stockpile.inventory.service.MovementService;
import com.stockpile.setup.dto.WarehouseGridSpec;
import com.stockpile.whatif.dto.WhatIfResult;
import com.stockpile.whatif.service.WhatIfService;

@SpringBootTest
@Testcontainers
@Transactional
class WhatIfServiceTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired WhatIfService whatIfService;
	@Autowired MovementService movementService;
	@Autowired SkuRepository skuRepository;
	@Autowired LotRepository lotRepository;
	@Autowired LocationRepository locationRepository;

	/** 1 zone × 1 aisle × 1 rack; a flat wide grid vs the current stacked one. */
	private static WarehouseGridSpec grid(int levels, int bins) {
		return new WarehouseGridSpec(1, 1, 1, levels, bins,
				BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(2), AccessFace.TOP);
	}

	@Test
	void flatterLayoutUnblocksStackedLots() {
		// Current warehouse: two lots stacked in one column -> ground lot blocked.
		Sku sku = sku("SHIRT");
		putaway(lot(sku), bin(0, 0, 0));
		putaway(lot(sku), bin(0, 0, 1));

		WhatIfResult r = whatIfService.simulate(grid(1, 4)); // flat: 4 bins, 1 level

		assertThat(r.current().placedLots()).isEqualTo(2);
		assertThat(r.current().blockedLots()).isEqualTo(1);
		assertThat(r.simulated().bins()).isEqualTo(4);
		assertThat(r.simulated().placedLots()).isEqualTo(2);
		assertThat(r.simulated().unplacedLots()).isZero();
		assertThat(r.simulated().blockedLots()).isZero(); // side by side now
		assertThat(r.simulated().fillRate()).isEqualTo(0.5);
	}

	@Test
	void tooSmallLayoutReportsUnplacedLots() {
		Sku sku = sku("SHIRT");
		putaway(lot(sku), bin(0, 0, 0));
		putaway(lot(sku), bin(5, 0, 0));
		putaway(lot(sku), bin(9, 0, 0));

		WhatIfResult r = whatIfService.simulate(grid(1, 2)); // only 2 slots

		assertThat(r.simulated().placedLots()).isEqualTo(2);
		assertThat(r.simulated().unplacedLots()).isEqualTo(1);
	}

	@Test
	void emptyWarehouseSimulatesToZeros() {
		WhatIfResult r = whatIfService.simulate(grid(2, 3));

		assertThat(r.current().placedLots()).isZero();
		assertThat(r.simulated().placedLots()).isZero();
		assertThat(r.simulated().bins()).isEqualTo(6);
		assertThat(r.simulated().fillRate()).isZero();
	}

	@Test
	void oversizedGridIsRejected() {
		assertThatThrownBy(() -> whatIfService.simulate(
				new WarehouseGridSpec(100, 100, 100, 10, 10,
						BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, AccessFace.TOP)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// --- helpers ---

	private Sku sku(String code) {
		Sku s = new Sku();
		s.setCode(code);
		s.setName(code);
		s.setW(BigDecimal.ONE);
		s.setD(BigDecimal.ONE);
		s.setH(BigDecimal.ONE);
		s.setWeight(BigDecimal.ONE);
		s.setHandling(HandlingType.FIFO);
		return skuRepository.save(s);
	}

	private Location bin(double x, double y, double z) {
		Location l = new Location();
		l.setZone("Z");
		l.setAisle("A");
		l.setRack("R");
		l.setLevel(String.valueOf((int) z));
		l.setBin("B-" + System.nanoTime());
		l.setX(BigDecimal.valueOf(x));
		l.setY(BigDecimal.valueOf(y));
		l.setZ(BigDecimal.valueOf(z));
		l.setW(BigDecimal.ONE);
		l.setD(BigDecimal.ONE);
		l.setH(BigDecimal.ONE);
		l.setLaneId("Z-A-R");
		l.setAccessFace(AccessFace.TOP);
		return locationRepository.save(l);
	}

	private Lot lot(Sku sku) {
		Lot lot = new Lot();
		lot.setSku(sku);
		lot.setW(new BigDecimal("0.8"));
		lot.setD(new BigDecimal("0.8"));
		lot.setH(new BigDecimal("0.8"));
		lot.setWeight(BigDecimal.ONE);
		return lotRepository.save(lot);
	}

	private void putaway(Lot lot, Location bin) {
		Movement m = new Movement();
		m.setLot(lot);
		m.setType(MovementType.PUTAWAY);
		m.setToBin(bin);
		movementService.record(m);
	}
}
