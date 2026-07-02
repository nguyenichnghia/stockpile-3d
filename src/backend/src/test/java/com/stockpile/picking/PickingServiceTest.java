package com.stockpile.picking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

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
import com.stockpile.picking.domain.OrderLine;
import com.stockpile.picking.domain.PickOrder;
import com.stockpile.picking.dto.PickPlan;
import com.stockpile.picking.dto.PickStep;
import com.stockpile.picking.repository.PickOrderRepository;
import com.stockpile.picking.service.PickingService;

/** Integration tests for the picking engine against a real Postgres. */
@SpringBootTest
@Testcontainers
@Transactional // roll back between tests so unique SKU/order codes don't collide
class PickingServiceTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired PickingService pickingService;
	@Autowired MovementService movementService;
	@Autowired SkuRepository skuRepository;
	@Autowired LotRepository lotRepository;
	@Autowired LocationRepository locationRepository;
	@Autowired PickOrderRepository orderRepository;

	@Test
	void accessibleLotYieldsASinglePickStep() {
		Sku sku = sku(HandlingType.FIFO);
		Lot lot = putaway(sku, bin("lane-p1", 0, 0, 0), null);
		PickOrder order = order(sku, 1);

		PickPlan plan = pickingService.plan(order.getId());

		assertThat(plan.steps()).hasSize(1);
		assertThat(plan.steps().get(0).kind()).isEqualTo(PickStep.Kind.PICK);
		assertThat(plan.steps().get(0).lotId()).isEqualTo(lot.getId());
		assertThat(plan.lines()).singleElement()
				.satisfies(l -> {
					assertThat(l.fulfilled()).isEqualTo(1);
					assertThat(l.shortfall()).isZero();
				});
	}

	@Test
	void blockedLotIsRelocatedBeforeBeingPicked() {
		// FEFO so the earliest-expiry lot is chosen even though it is blocked —
		// perishable stock is picked in order, forcing a relocation.
		Sku sku = sku(HandlingType.FEFO);
		// Stack: target at bottom (z=0), a blocker on top (z=2), plus an empty slot.
		Location bottom = bin("lane-p2", 0, 0, 0);
		Location top = bin("lane-p2", 0, 0, 2);
		bin("lane-p2", 5, 5, 0); // empty temp slot in the lane
		Lot target = putaway(sku, bottom, LocalDate.of(2026, 1, 1)); // earliest expiry -> chosen
		putaway(sku, top, LocalDate.of(2026, 12, 1));

		PickOrder order = order(sku, 1);
		PickPlan plan = pickingService.plan(order.getId());

		// A RELOCATE (clear the blocker) must come before the PICK of the target.
		assertThat(plan.steps()).extracting(PickStep::kind)
				.containsExactly(PickStep.Kind.RELOCATE, PickStep.Kind.PICK);
		assertThat(plan.steps().get(1).lotId()).isEqualTo(target.getId());
	}

	@Test
	void insufficientStockReportsShortfall() {
		Sku sku = sku(HandlingType.FIFO);
		putaway(sku, bin("lane-p3", 0, 0, 0), null); // only one lot
		PickOrder order = order(sku, 3); // asked for three

		PickPlan plan = pickingService.plan(order.getId());

		assertThat(plan.steps()).hasSize(1); // only what exists
		assertThat(plan.lines()).singleElement()
				.satisfies(l -> {
					assertThat(l.requested()).isEqualTo(3);
					assertThat(l.fulfilled()).isEqualTo(1);
					assertThat(l.shortfall()).isEqualTo(2);
				});
	}

	// --- helpers ---

	private Sku sku(HandlingType handling) {
		Sku s = new Sku();
		s.setCode("S-" + System.nanoTime());
		s.setName("t");
		s.setW(BigDecimal.ONE);
		s.setD(BigDecimal.ONE);
		s.setH(BigDecimal.ONE);
		s.setWeight(BigDecimal.ONE);
		s.setHandling(handling);
		return skuRepository.save(s);
	}

	private Location bin(String lane, double x, double y, double z) {
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

	private Lot putaway(Sku sku, Location bin, LocalDate expiry) {
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
		return lot;
	}

	private PickOrder order(Sku sku, int qty) {
		PickOrder o = new PickOrder();
		o.setCode("O-" + System.nanoTime());
		OrderLine line = new OrderLine();
		line.setSku(sku);
		line.setQty(qty);
		o.addLine(line);
		return orderRepository.save(o);
	}
}
