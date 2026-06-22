package com.stockpile.putaway;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.stockpile.inventory.domain.Sku;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.SkuRepository;
import com.stockpile.putaway.dto.PutawaySuggestion;
import com.stockpile.putaway.service.PutawayService;

@SpringBootTest
@Testcontainers
@Transactional // each test rolls back, so findEmpty() isn't polluted across methods
class PutawayServiceTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired PutawayService putawayService;
	@Autowired SkuRepository skuRepository;
	@Autowired LotRepository lotRepository;
	@Autowired LocationRepository locationRepository;

	@Test
	void recommendsBinNearestDockAndLowest() {
		Lot lot = newLot(false);
		// near + low (best), far, and high — all empty and big enough.
		Location near = newLocation("NEAR", 1, 0, 0);
		newLocation("FAR", 50, 50, 0);
		newLocation("HIGH", 1, 0, 10);

		PutawaySuggestion s = putawayService.suggest(lot.getId());

		assertThat(s.recommendedBinId()).isEqualTo(near.getId());
		assertThat(s.candidates()).hasSize(3);
		// scores are sorted ascending (best first)
		assertThat(s.candidates().get(0).score())
				.isLessThanOrEqualTo(s.candidates().get(1).score());
	}

	@Test
	void skipsBinsTooSmallForTheLot() {
		Lot big = newLot(false); // 1x1x1
		newLocationSized("TINY", 0, 0, 0, new BigDecimal("0.5")); // too small
		Location ok = newLocation("OK", 2, 0, 0);

		PutawaySuggestion s = putawayService.suggest(big.getId());

		assertThat(s.candidates()).extracting(PutawaySuggestion.ScoredBin::binId)
				.containsExactly(ok.getId());
	}

	@Test
	void noFittingBinGivesNoRecommendation() {
		Lot lot = newLot(false);
		newLocationSized("SMALL", 0, 0, 0, new BigDecimal("0.1"));

		PutawaySuggestion s = putawayService.suggest(lot.getId());

		assertThat(s.recommendedBinId()).isNull();
		assertThat(s.candidates()).isEmpty();
	}

	// --- helpers ---

	private Sku newSku() {
		Sku sku = new Sku();
		sku.setCode("SKU-" + System.nanoTime());
		sku.setName("test");
		sku.setW(BigDecimal.ONE);
		sku.setD(BigDecimal.ONE);
		sku.setH(BigDecimal.ONE);
		sku.setWeight(BigDecimal.ONE);
		sku.setHandling(HandlingType.FIFO);
		return skuRepository.save(sku);
	}

	private Lot newLot(boolean withExpiry) {
		Lot lot = new Lot();
		lot.setSku(newSku());
		lot.setW(BigDecimal.ONE);
		lot.setD(BigDecimal.ONE);
		lot.setH(BigDecimal.ONE);
		lot.setWeight(BigDecimal.ONE);
		if (withExpiry) {
			lot.setExpiry(java.time.LocalDate.now().plusDays(7));
		}
		return lotRepository.save(lot);
	}

	private Location newLocation(String bin, double x, double y, double z) {
		return newLocationSized(bin, x, y, z, new BigDecimal("2"));
	}

	private Location newLocationSized(String bin, double x, double y, double z, BigDecimal size) {
		Location l = new Location();
		l.setZone("Z");
		l.setAisle("A");
		l.setRack("R");
		l.setLevel("1");
		l.setBin(bin + "-" + System.nanoTime());
		l.setX(BigDecimal.valueOf(x));
		l.setY(BigDecimal.valueOf(y));
		l.setZ(BigDecimal.valueOf(z));
		l.setW(size);
		l.setD(size);
		l.setH(size);
		l.setLaneId("lane-1");
		l.setAccessFace(AccessFace.TOP);
		return locationRepository.save(l);
	}
}
