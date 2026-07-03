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
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.SkuRepository;
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

	@Test
	void resolvesAPlacedLotWithItsBin() {
		Location bin = binWithCode("A", "01", "00", "1", "01");
		Lot lot = putaway(sku("SHIRT"), bin);

		ScanResult result = scanService.resolve("LOT-" + lot.getId());

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

		ScanResult result = scanService.resolve("lot-" + lot.getId()); // case-insensitive

		assertThat(result.found()).isTrue();
		assertThat(result.lot().binId()).isNull();
	}

	@Test
	void unknownLotIdKeepsTypeButIsNotFound() {
		ScanResult result = scanService.resolve("LOT-999999");

		assertThat(result.type()).isEqualTo(ScanResult.Type.LOT);
		assertThat(result.found()).isFalse();
		assertThat(result.lot()).isNull();
	}

	@Test
	void resolvesABinWithItsLots() {
		Location bin = binWithCode("A", "01", "00", "1", "01");
		Lot lot = putaway(sku("SHIRT"), bin);

		ScanResult result = scanService.resolve(" A-01-00-1-01 "); // trimmed

		assertThat(result.type()).isEqualTo(ScanResult.Type.BIN);
		assertThat(result.found()).isTrue();
		assertThat(result.bin().id()).isEqualTo(bin.getId());
		assertThat(result.bin().lotIds()).containsExactly(lot.getId());
	}

	@Test
	void resolvesAnEmptyBinWithNoLots() {
		binWithCode("A", "01", "00", "1", "01");

		ScanResult result = scanService.resolve("A-01-00-1-01");

		assertThat(result.found()).isTrue();
		assertThat(result.bin().lotIds()).isEmpty();
	}

	@Test
	void unknownBinCodeKeepsTypeButIsNotFound() {
		ScanResult result = scanService.resolve("Z-99-99-9-99");

		assertThat(result.type()).isEqualTo(ScanResult.Type.BIN);
		assertThat(result.found()).isFalse();
	}

	@Test
	void unrecognizedShapeIsUnknown() {
		assertThat(scanService.resolve("HELLO").type()).isEqualTo(ScanResult.Type.UNKNOWN);
		assertThat(scanService.resolve("LOT-abc").type()).isEqualTo(ScanResult.Type.UNKNOWN);
		assertThat(scanService.resolve("A-01-00").type()).isEqualTo(ScanResult.Type.UNKNOWN);
	}

	@Test
	void blankCodeIsRejected() {
		assertThatThrownBy(() -> scanService.resolve("  "))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// --- helpers (same shapes as LocateServiceTest) ---

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
		Location l = new Location();
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
