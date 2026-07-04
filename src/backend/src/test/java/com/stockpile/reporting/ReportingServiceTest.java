package com.stockpile.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import com.stockpile.picking.domain.PickOrder;
import com.stockpile.picking.repository.PickOrderRepository;
import com.stockpile.reporting.dto.MovementDaily;
import com.stockpile.reporting.dto.ReportSummary;
import com.stockpile.reporting.service.ReportingService;

@SpringBootTest
@Testcontainers
@Transactional // each test rolls back
class ReportingServiceTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired ReportingService reportingService;
	@Autowired MovementService movementService;
	@Autowired SkuRepository skuRepository;
	@Autowired LotRepository lotRepository;
	@Autowired LocationRepository locationRepository;
	@Autowired PickOrderRepository pickOrderRepository;
	@Autowired WarehouseRepository warehouseRepository;

	private Warehouse wh;

	@Test
	void emptyWarehouseSummarizesToZeros() {
		ReportSummary s = reportingService.summary(warehouse().getId());

		assertThat(s.totalBins()).isZero();
		assertThat(s.fillRate()).isZero();
		assertThat(s.activeLots()).isZero();
		assertThat(s.blockedLots()).isZero();
		assertThat(s.openOrders()).isZero();
	}

	@Test
	void countsOccupancyBlockingExpiryAndThroughput() {
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		Sku sku = sku("SHIRT");

		// Stack in one lane: ground lot blocked by the lot on top; a third bin
		// stays empty. Ground lot expired, top lot expiring within the horizon.
		Location ground = bin("L1", 0, 0, 0);
		Location top = bin("L1", 0, 0, 1);
		bin("L1", 5, 0, 0); // empty

		putaway(lot(sku, today.minusDays(1)), ground);
		putaway(lot(sku, today.plusDays(5)), top);

		PickOrder order = new PickOrder();
		order.setCode("ORD-R");
		order.setWarehouse(warehouse());
		pickOrderRepository.save(order);

		ReportSummary s = reportingService.summary(warehouse().getId());

		assertThat(s.totalBins()).isEqualTo(3);
		assertThat(s.occupiedBins()).isEqualTo(2);
		assertThat(s.fillRate()).isEqualTo(2.0 / 3.0);
		assertThat(s.activeLots()).isEqualTo(2);
		assertThat(s.blockedLots()).isEqualTo(1); // only the ground lot is buried
		assertThat(s.expired()).isEqualTo(1);
		assertThat(s.expiringSoon()).isEqualTo(1);
		assertThat(s.openOrders()).isEqualTo(1);
		assertThat(s.movementsToday()).isEqualTo(2); // the two putaways above
	}

	@Test
	void movementsDailyGroupsByDayAndType() {
		Sku sku = sku("SHIRT");
		Location a = bin("L1", 0, 0, 0);
		Location b = bin("L1", 5, 0, 0);
		Lot lot = putaway(lot(sku, null), a);
		relocate(lot, a, b);

		List<MovementDaily> rows = reportingService.movementsDaily(7, warehouse().getId());

		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		assertThat(rows).containsExactlyInAnyOrder(
				new MovementDaily(today, MovementType.PUTAWAY.name(), 1),
				new MovementDaily(today, MovementType.RELOCATE.name(), 1));
	}

	@Test
	void movementsDailyRejectsAnOutOfRangeWindow() {
		assertThatThrownBy(() -> reportingService.movementsDaily(0, warehouse().getId()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> reportingService.movementsDaily(91, warehouse().getId()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// --- helpers (same shapes as the other service tests) ---

	/** The single test warehouse, created lazily (rolled back between tests). */
	private Warehouse warehouse() {
		if (wh == null) {
			Warehouse w = new Warehouse();
			w.setCode("WH-" + System.nanoTime());
			w.setName("Test warehouse");
			wh = warehouseRepository.save(w);
		}
		return wh;
	}

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

	/** A 1×1×1 bin at (x,y,z) in the given lane; bin code kept unique. */
	private Location bin(String lane, double x, double y, double z) {
		Location l = new Location();
		l.setWarehouse(warehouse());
		l.setZone("Z");
		l.setAisle("A");
		l.setRack(lane);
		l.setLevel(String.valueOf((int) z));
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

	private Lot lot(Sku sku, LocalDate expiry) {
		Lot lot = new Lot();
		lot.setSku(sku);
		lot.setW(BigDecimal.ONE);
		lot.setD(BigDecimal.ONE);
		lot.setH(BigDecimal.ONE);
		lot.setWeight(BigDecimal.ONE);
		lot.setExpiry(expiry);
		return lotRepository.save(lot);
	}

	private Lot putaway(Lot lot, Location bin) {
		Movement m = new Movement();
		m.setLot(lot);
		m.setType(MovementType.PUTAWAY);
		m.setToBin(bin);
		movementService.record(m);
		return lot;
	}

	private void relocate(Lot lot, Location from, Location to) {
		Movement m = new Movement();
		m.setLot(lot);
		m.setType(MovementType.RELOCATE);
		m.setFromBin(from);
		m.setToBin(to);
		movementService.record(m);
	}
}
