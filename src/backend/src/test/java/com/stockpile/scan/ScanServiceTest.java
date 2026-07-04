package com.stockpile.scan;

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
import com.stockpile.inventory.domain.Warehouse;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.SkuRepository;
import com.stockpile.inventory.repository.WarehouseRepository;
import com.stockpile.inventory.service.MovementService;
import com.stockpile.scan.dto.ScanResult;
import com.stockpile.scan.service.ScanService;

@SpringBootTest
@Testcontainers
@Transactional // each test rolls back, so codes aren't reused across methods
class ScanServiceTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired ScanService scanService;
	@Autowired MovementService movementService;
	@Autowired SkuRepository skuRepository;
	@Autowired LotRepository lotRepository;
	@Autowired LocationRepository locationRepository;
	@Autowired WarehouseRepository warehouseRepository;

	private Warehouse wh;

	@Test
	void resolvesAPlacedLotWithItsBin() {
		Location bin = binWithCode("A", "01", "00", "1", "01");
		Lot lot = putaway(sku("SHIRT"), bin);

		ScanResult result = scanService.resolve("LOT-" + lot.getId(), warehouse().getId());

		assertThat(result.type()).isEqualTo(ScanResult.Type.LOT);
		assertThat(result.found()).isTrue();
		assertThat(result.lot().sku()).isEqualTo("SHIRT");
		assertThat(result.lot().binId()).isEqualTo(bin.getId());
		assertThat(result.lot().binCode()).isEqualTo("A-01-00-1-01");
		assertThat(result.lot().laneId()).isEqualTo(bin.getLaneId());
	}

	@Test
	void resolvesAnUnplacedLotWithoutABin() {
		Lot lot = lot(sku("SHIRT")); // exists, but never put away

		ScanResult result = scanService.resolve("lot-" + lot.getId(), warehouse().getId()); // case-insensitive

		assertThat(result.found()).isTrue();
		assertThat(result.lot().binId()).isNull();
	}

	@Test
	void unknownLotIdKeepsTypeButIsNotFound() {
		ScanResult result = scanService.resolve("LOT-999999", warehouse().getId());

		assertThat(result.type()).isEqualTo(ScanResult.Type.LOT);
		assertThat(result.found()).isFalse();
		assertThat(result.lot()).isNull();
	}

	@Test
	void resolvesABinWithItsLots() {
		Location bin = binWithCode("A", "01", "00", "1", "01");
		Lot lot = putaway(sku("SHIRT"), bin);

		ScanResult result = scanService.resolve(" A-01-00-1-01 ", warehouse().getId()); // trimmed

		assertThat(result.type()).isEqualTo(ScanResult.Type.BIN);
		assertThat(result.found()).isTrue();
		assertThat(result.bin().id()).isEqualTo(bin.getId());
		assertThat(result.bin().lotIds()).containsExactly(lot.getId());
	}

	@Test
	void resolvesAnEmptyBinWithNoLots() {
		binWithCode("A", "01", "00", "1", "01");

		ScanResult result = scanService.resolve("A-01-00-1-01", warehouse().getId());

		assertThat(result.found()).isTrue();
		assertThat(result.bin().lotIds()).isEmpty();
	}

	@Test
	void unknownBinCodeKeepsTypeButIsNotFound() {
		ScanResult result = scanService.resolve("Z-99-99-9-99", warehouse().getId());

		assertThat(result.type()).isEqualTo(ScanResult.Type.BIN);
		assertThat(result.found()).isFalse();
	}

	@Test
	void binCodeResolvesWithinTheSelectedWarehouse() {
		// The same 5-segment code may exist in two warehouses (ADR-0009): the
		// scan must resolve to the bin of the warehouse the caller works in.
		Location mine = binWithCode("A", "01", "00", "1", "01");
		Warehouse other = newWarehouse();
		Location theirs = binInWarehouse(other, "A", "01", "00", "1", "01");

		ScanResult result = scanService.resolve("A-01-00-1-01", warehouse().getId());
		ScanResult otherResult = scanService.resolve("A-01-00-1-01", other.getId());

		assertThat(result.bin().id()).isEqualTo(mine.getId());
		assertThat(otherResult.bin().id()).isEqualTo(theirs.getId());
	}

	@Test
	void unrecognizedShapeIsUnknown() {
		Long whId = warehouse().getId();
		assertThat(scanService.resolve("HELLO", whId).type()).isEqualTo(ScanResult.Type.UNKNOWN);
		assertThat(scanService.resolve("LOT-abc", whId).type()).isEqualTo(ScanResult.Type.UNKNOWN);
		assertThat(scanService.resolve("A-01-00", whId).type()).isEqualTo(ScanResult.Type.UNKNOWN);
	}

	@Test
	void blankCodeIsRejected() {
		assertThatThrownBy(() -> scanService.resolve("  ", warehouse().getId()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// --- helpers (same shapes as LocateServiceTest) ---

	/** The default test warehouse, created lazily (rolled back between tests). */
	private Warehouse warehouse() {
		if (wh == null) {
			wh = newWarehouse();
		}
		return wh;
	}

	private Warehouse newWarehouse() {
		Warehouse w = new Warehouse();
		w.setCode("WH-" + System.nanoTime());
		w.setName("Test warehouse");
		return warehouseRepository.save(w);
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

	private Location binWithCode(String zone, String aisle, String rack, String level, String bin) {
		return binInWarehouse(warehouse(), zone, aisle, rack, level, bin);
	}

	private Location binInWarehouse(Warehouse warehouse,
			String zone, String aisle, String rack, String level, String bin) {
		Location l = new Location();
		l.setWarehouse(warehouse);
		l.setZone(zone);
		l.setAisle(aisle);
		l.setRack(rack);
		l.setLevel(level);
		l.setBin(bin);
		l.setX(BigDecimal.ZERO);
		l.setY(BigDecimal.ZERO);
		l.setZ(BigDecimal.ZERO);
		l.setW(BigDecimal.ONE);
		l.setD(BigDecimal.ONE);
		l.setH(BigDecimal.ONE);
		l.setLaneId(zone + "-" + aisle + "-" + rack);
		l.setAccessFace(AccessFace.TOP);
		return locationRepository.save(l);
	}

	private Lot lot(Sku sku) {
		Lot lot = new Lot();
		lot.setSku(sku);
		lot.setW(BigDecimal.ONE);
		lot.setD(BigDecimal.ONE);
		lot.setH(BigDecimal.ONE);
		lot.setWeight(BigDecimal.ONE);
		return lotRepository.save(lot);
	}

	private Lot putaway(Sku sku, Location bin) {
		Lot lot = lot(sku);
		Movement m = new Movement();
		m.setLot(lot);
		m.setType(MovementType.PUTAWAY);
		m.setToBin(bin);
		movementService.record(m);
		return lot;
	}
}
