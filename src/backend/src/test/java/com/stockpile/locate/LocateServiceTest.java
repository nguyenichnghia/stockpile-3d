package com.stockpile.locate;

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
import com.stockpile.locate.dto.LocateResult;
import com.stockpile.locate.service.LocateService;

@SpringBootTest
@Testcontainers
@Transactional // each test rolls back, so a SKU code isn't reused across methods
class LocateServiceTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired LocateService locateService;
	@Autowired MovementService movementService;
	@Autowired SkuRepository skuRepository;
	@Autowired LotRepository lotRepository;
	@Autowired LocationRepository locationRepository;

	@Test
	void findsEveryLotOfTheSku() {
		// Two lots of SHIRT in two different bins, plus an unrelated PANTS lot.
		Sku shirt = sku("SHIRT");
		putaway(shirt, bin(0, 0, 0));
		putaway(shirt, bin(5, 0, 0));
		putaway(sku("PANTS"), bin(9, 0, 0));

		LocateResult result = locateService.locateBySku("SHIRT");

		assertThat(result.sku()).isEqualTo("SHIRT");
		assertThat(result.matchCount()).isEqualTo(2);
		assertThat(result.matches()).extracting(LocateResult.Match::binId).doesNotContainNull();
	}

	@Test
	void matchIsCaseInsensitiveAndTrimmed() {
		putaway(sku("SHIRT"), bin(0, 0, 0));

		assertThat(locateService.locateBySku("  shirt  ").matchCount()).isEqualTo(1);
	}

	@Test
	void unknownSkuReturnsNoMatches() {
		putaway(sku("SHIRT"), bin(0, 0, 0));

		LocateResult result = locateService.locateBySku("NOPE");
		assertThat(result.matchCount()).isZero();
		assertThat(result.matches()).isEmpty();
	}

	@Test
	void blankSkuIsRejected() {
		assertThatThrownBy(() -> locateService.locateBySku("  "))
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
		l.setLevel("1");
		l.setBin("B-" + System.nanoTime());
		l.setX(BigDecimal.valueOf(x));
		l.setY(BigDecimal.valueOf(y));
		l.setZ(BigDecimal.valueOf(z));
		l.setW(BigDecimal.ONE);
		l.setD(BigDecimal.ONE);
		l.setH(BigDecimal.ONE);
		l.setLaneId("lane-" + System.nanoTime());
		l.setAccessFace(AccessFace.TOP);
		return locationRepository.save(l);
	}

	private Lot putaway(Sku sku, Location bin) {
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
		return lot;
	}
}
