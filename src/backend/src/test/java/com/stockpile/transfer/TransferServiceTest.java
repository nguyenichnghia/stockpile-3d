package com.stockpile.transfer;

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

import com.stockpile.common.NotFoundException;
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
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.inventory.repository.SkuRepository;
import com.stockpile.inventory.repository.WarehouseRepository;
import com.stockpile.inventory.service.MovementService;
import com.stockpile.transfer.domain.TransferStatus;
import com.stockpile.transfer.dto.TransferDto;
import com.stockpile.transfer.service.TransferService;

/**
 * Cross-warehouse transfer (ADR-0010, Phương án B): a lot moves from A to B as a
 * linked OUTBOUND (A) + INBOUND (B). Verifies the two-step lifecycle and its
 * guards, and that the position stays derived from the ledger throughout.
 */
@SpringBootTest
@Testcontainers
@Transactional
class TransferServiceTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired TransferService transferService;
	@Autowired MovementService movementService;
	@Autowired SkuRepository skuRepository;
	@Autowired LotRepository lotRepository;
	@Autowired LocationRepository locationRepository;
	@Autowired PlacementRepository placementRepository;
	@Autowired WarehouseRepository warehouseRepository;

	@Test
	void openThenReceiveMovesLotAcrossWarehousesViaLedger() {
		Warehouse a = warehouse("WH-A");
		Warehouse b = warehouse("WH-B");
		Sku sku = sku("SHIRT");
		Lot lot = lot(sku);
		Location binA = bin(a, "BIN-A");
		Location binB = bin(b, "BIN-B");
		putaway(lot, binA);

		// Open: OUTBOUND in A, lot goes in-transit (placement dropped).
		TransferDto t = transferService.open(lot.getId(), b.getId());
		assertThat(t.status()).isEqualTo(TransferStatus.IN_TRANSIT);
		assertThat(t.fromWarehouseId()).isEqualTo(a.getId());
		assertThat(t.toWarehouseId()).isEqualTo(b.getId());
		assertThat(t.outboundMovementId()).isNotNull();
		assertThat(t.inboundMovementId()).isNull();
		assertThat(placementRepository.findByLotId(lot.getId())).isEmpty(); // in transit
		assertThat(transferService.incoming(b.getId())).hasSize(1);

		// Receive into a bin of B: INBOUND, transfer completed, lot now in B.
		TransferDto done = transferService.receive(t.id(), binB.getId());
		assertThat(done.status()).isEqualTo(TransferStatus.COMPLETED);
		assertThat(done.inboundMovementId()).isNotNull();
		assertThat(done.completedAt()).isNotNull();

		Location placedBin = placementRepository.findByLotId(lot.getId()).orElseThrow().getBin();
		assertThat(placedBin.getId()).isEqualTo(binB.getId());
		assertThat(placedBin.getWarehouse().getId()).isEqualTo(b.getId());
		assertThat(transferService.incoming(b.getId())).isEmpty(); // no longer in transit
	}

	@Test
	void cannotTransferAnUnplacedLot() {
		Warehouse a = warehouse("WH-A");
		Warehouse b = warehouse("WH-B");
		Lot lot = lot(sku("SHIRT")); // never placed
		bin(a, "BIN-A");

		assertThatThrownBy(() -> transferService.open(lot.getId(), b.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("not placed");
	}

	@Test
	void cannotTransferToTheSameWarehouse() {
		Warehouse a = warehouse("WH-A");
		Sku sku = sku("SHIRT");
		Lot lot = lot(sku);
		putaway(lot, bin(a, "BIN-A"));

		assertThatThrownBy(() -> transferService.open(lot.getId(), a.getId()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must cross warehouses");
	}

	@Test
	void cannotOpenASecondTransferWhileInTransit() {
		Warehouse a = warehouse("WH-A");
		Warehouse b = warehouse("WH-B");
		Sku sku = sku("SHIRT");
		Lot lot = lot(sku);
		putaway(lot, bin(a, "BIN-A"));

		transferService.open(lot.getId(), b.getId());
		assertThatThrownBy(() -> transferService.open(lot.getId(), b.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("already in transit");
	}

	@Test
	void cannotReceiveIntoABinOfTheWrongWarehouse() {
		Warehouse a = warehouse("WH-A");
		Warehouse b = warehouse("WH-B");
		Warehouse c = warehouse("WH-C");
		Sku sku = sku("SHIRT");
		Lot lot = lot(sku);
		putaway(lot, bin(a, "BIN-A"));
		Location binC = bin(c, "BIN-C");

		TransferDto t = transferService.open(lot.getId(), b.getId());
		assertThatThrownBy(() -> transferService.receive(t.id(), binC.getId()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("destination");
	}

	@Test
	void cannotReceiveTwice() {
		Warehouse a = warehouse("WH-A");
		Warehouse b = warehouse("WH-B");
		Sku sku = sku("SHIRT");
		Lot lot = lot(sku);
		putaway(lot, bin(a, "BIN-A"));
		Location binB = bin(b, "BIN-B");

		TransferDto t = transferService.open(lot.getId(), b.getId());
		transferService.receive(t.id(), binB.getId());
		assertThatThrownBy(() -> transferService.receive(t.id(), binB.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("not in transit");
	}

	@Test
	void openingUnknownLotIs404() {
		Warehouse b = warehouse("WH-B");
		assertThatThrownBy(() -> transferService.open(999_999L, b.getId()))
				.isInstanceOf(NotFoundException.class);
	}

	// --- helpers ---

	private Warehouse warehouse(String prefix) {
		Warehouse w = new Warehouse();
		w.setCode(prefix + "-" + System.nanoTime());
		w.setName(prefix);
		return warehouseRepository.save(w);
	}

	private Sku sku(String code) {
		Sku s = new Sku();
		s.setCode(code + "-" + System.nanoTime());
		s.setName(code);
		s.setW(BigDecimal.ONE);
		s.setD(BigDecimal.ONE);
		s.setH(BigDecimal.ONE);
		s.setWeight(BigDecimal.ONE);
		s.setHandling(HandlingType.FIFO);
		return skuRepository.save(s);
	}

	private Location bin(Warehouse warehouse, String code) {
		Location l = new Location();
		l.setWarehouse(warehouse);
		l.setZone("Z");
		l.setAisle("A");
		l.setRack("R");
		l.setLevel("1");
		l.setBin(code + "-" + System.nanoTime());
		l.setX(BigDecimal.ZERO);
		l.setY(BigDecimal.ZERO);
		l.setZ(BigDecimal.ZERO);
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
		lot.setW(BigDecimal.ONE);
		lot.setD(BigDecimal.ONE);
		lot.setH(BigDecimal.ONE);
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
