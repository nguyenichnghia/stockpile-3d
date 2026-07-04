package com.stockpile.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.stockpile.inventory.domain.Placement;
import com.stockpile.inventory.domain.Sku;
import com.stockpile.inventory.domain.Warehouse;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.inventory.repository.SkuRepository;
import com.stockpile.inventory.repository.WarehouseRepository;
import com.stockpile.inventory.service.MovementService;
import com.stockpile.inventory.service.PlacementProjectionService;

@SpringBootTest
@Testcontainers
class PlacementProjectionTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired MovementService movementService;
	@Autowired PlacementProjectionService projectionService;
	@Autowired PlacementRepository placementRepository;
	@Autowired LocationRepository locationRepository;
	@Autowired LotRepository lotRepository;
	@Autowired SkuRepository skuRepository;
	@Autowired WarehouseRepository warehouseRepository;

	private Warehouse wh;

	@Test
	void putawayCreatesPlacementAtBinCorner() {
		Lot lot = newLot();
		Location bin = newLocation("L1", new BigDecimal("1.0"), new BigDecimal("2.0"), new BigDecimal("3.0"));

		movementService.record(movement(lot, MovementType.PUTAWAY, null, bin));

		Placement placement = placementRepository.findByLotId(lot.getId()).orElseThrow();
		assertThat(placement.getBin().getId()).isEqualTo(bin.getId());
		assertThat(placement.getX()).isEqualByComparingTo("1.0");
		assertThat(placement.getY()).isEqualByComparingTo("2.0");
		assertThat(placement.getZ()).isEqualByComparingTo("3.0");
	}

	@Test
	void relocateMovesPlacementToNewBin() {
		Lot lot = newLot();
		Location binA = newLocation("A", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
		Location binB = newLocation("B", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO);

		movementService.record(movement(lot, MovementType.PUTAWAY, null, binA));
		movementService.record(movement(lot, MovementType.RELOCATE, binA, binB));

		Placement placement = placementRepository.findByLotId(lot.getId()).orElseThrow();
		assertThat(placement.getBin().getId()).isEqualTo(binB.getId());
	}

	@Test
	void pickRemovesPlacement() {
		Lot lot = newLot();
		Location bin = newLocation("P", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO);

		movementService.record(movement(lot, MovementType.PUTAWAY, null, bin));
		movementService.record(movement(lot, MovementType.PICK, bin, null));

		assertThat(placementRepository.findByLotId(lot.getId())).isEmpty();
	}

	@Test
	void incrementalProjectionMatchesLedgerReplay() {
		Lot lot1 = newLot();
		Lot lot2 = newLot();
		Location binA = newLocation("RA", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
		Location binB = newLocation("RB", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);

		// A representative history exercising every transition.
		movementService.record(movement(lot1, MovementType.PUTAWAY, null, binA));
		movementService.record(movement(lot2, MovementType.INBOUND, null, binB));
		movementService.record(movement(lot1, MovementType.RELOCATE, binA, binB));
		movementService.record(movement(lot2, MovementType.PICK, binB, null));

		Map<Long, Long> incremental = placementsByLot();

		projectionService.rebuildAll();
		Map<Long, Long> rebuilt = placementsByLot();

		assertThat(rebuilt).isEqualTo(incremental);
		// lot1 ended at binB; lot2 was picked out.
		assertThat(incremental).containsEntry(lot1.getId(), binB.getId());
		assertThat(incremental).doesNotContainKey(lot2.getId());
	}

	@Test
	void relocateWithoutPlacementFails() {
		Lot lot = newLot();
		Location bin = newLocation("X", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

		assertThatThrownBy(() -> movementService.record(movement(lot, MovementType.RELOCATE, null, bin)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void movementAcrossWarehousesIsRejected() {
		// v1 has no cross-warehouse transfers (ADR-0009): the ledger refuses a
		// movement whose bins belong to two different warehouses.
		Lot lot = newLot();
		Location here = newLocation("H", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
		Location elsewhere = newLocationIn(newWarehouse(), "E");

		movementService.record(movement(lot, MovementType.PUTAWAY, null, here));

		assertThatThrownBy(() -> movementService.record(
				movement(lot, MovementType.RELOCATE, here, elsewhere)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("crosses warehouses");
	}

	@Test
	void movementWithoutBinsRequiresAWarehouse() {
		Lot lot = newLot();

		// INBOUND to staging carries no bins, so the warehouse must be stated…
		assertThatThrownBy(() -> movementService.record(movement(lot, MovementType.INBOUND, null, null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("warehouseId is required");

		// …and with it stated, the ledger records the event in that warehouse.
		Movement staged = movement(lot, MovementType.INBOUND, null, null);
		staged.setWarehouse(warehouse());
		Movement saved = movementService.record(staged);
		assertThat(saved.getWarehouse().getId()).isEqualTo(warehouse().getId());
	}

	// --- helpers ---

	/** Current placements as lot id -> bin id, for order-independent comparison. */
	private Map<Long, Long> placementsByLot() {
		return placementRepository.findAll().stream()
				.collect(Collectors.toMap(p -> p.getLot().getId(), p -> p.getBin().getId()));
	}

	/** The default test warehouse (bin codes stay unique via nanoTime). */
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

	private Sku newSku() {
		Sku sku = new Sku();
		sku.setCode("SKU-" + System.nanoTime());
		sku.setName("test sku");
		sku.setW(BigDecimal.ONE);
		sku.setD(BigDecimal.ONE);
		sku.setH(BigDecimal.ONE);
		sku.setWeight(BigDecimal.ONE);
		sku.setHandling(HandlingType.FIFO);
		return skuRepository.save(sku);
	}

	private Lot newLot() {
		Lot lot = new Lot();
		lot.setSku(newSku());
		lot.setW(BigDecimal.ONE);
		lot.setD(BigDecimal.ONE);
		lot.setH(BigDecimal.ONE);
		lot.setWeight(BigDecimal.ONE);
		return lotRepository.save(lot);
	}

	private Location newLocation(String bin, BigDecimal x, BigDecimal y, BigDecimal z) {
		return newLocationAt(warehouse(), bin, x, y, z);
	}

	private Location newLocationIn(Warehouse warehouse, String bin) {
		return newLocationAt(warehouse, bin, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
	}

	private Location newLocationAt(Warehouse warehouse, String bin, BigDecimal x, BigDecimal y, BigDecimal z) {
		Location location = new Location();
		location.setWarehouse(warehouse);
		location.setZone("Z");
		location.setAisle("A");
		location.setRack("R");
		location.setLevel("1");
		location.setBin(bin + "-" + System.nanoTime());
		location.setX(x);
		location.setY(y);
		location.setZ(z);
		location.setW(BigDecimal.ONE);
		location.setD(BigDecimal.ONE);
		location.setH(BigDecimal.ONE);
		location.setLaneId("lane-1");
		location.setAccessFace(AccessFace.NORTH);
		return locationRepository.save(location);
	}

	private Movement movement(Lot lot, MovementType type, Location from, Location to) {
		Movement movement = new Movement();
		movement.setLot(lot);
		movement.setType(type);
		movement.setFromBin(from);
		movement.setToBin(to);
		return movement;
	}
}
