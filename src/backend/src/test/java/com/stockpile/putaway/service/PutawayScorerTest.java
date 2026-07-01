package com.stockpile.putaway.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockpile.inventory.domain.AccessFace;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.relocation.service.LotBox;

/** Pure unit tests for SLAP scoring — no DB, runs in milliseconds. */
class PutawayScorerTest {

	private static final PutawayWeights W = new PutawayWeights(1, 10, 2, 5);

	@Test
	void fitsRejectsAnOversizeLot() {
		Lot lot = lot(2, 2, 2, null);
		assertThat(PutawayScorer.fits(lot, bin(0, 0, 0, 1, 1, 1))).isFalse();
		assertThat(PutawayScorer.fits(lot, bin(0, 0, 0, 2, 2, 2))).isTrue();
	}

	@Test
	void distToDockIsEuclideanFromOrigin() {
		assertThat(PutawayScorer.distToDock(bin(3, 4, 0, 1, 1, 1))).isEqualTo(5.0);
	}

	@Test
	void fitPenaltyIsWastedVolume() {
		Lot lot = lot(1, 1, 1, null);
		// bin volume 2*2*2=8, lot 1 -> waste 7.
		assertThat(PutawayScorer.fitPenalty(lot, bin(0, 0, 0, 2, 2, 2))).isEqualTo(7.0);
	}

	@Test
	void retrievalMisalignmentDoublesForLotsWithExpiry() {
		Location high = bin(0, 0, 3, 1, 1, 1);
		assertThat(PutawayScorer.retrievalMisalignment(lot(1, 1, 1, null), high)).isEqualTo(3.0);
		assertThat(PutawayScorer.retrievalMisalignment(lot(1, 1, 1, LocalDate.now()), high))
				.isEqualTo(6.0);
	}

	@Test
	void blockingPenaltyFiresWhenStackingOntoAnExistingLot() {
		Lot lot = lot(1, 1, 1, null);
		Location onTop = bin(0, 0, 1, 1, 1, 1); // sits flush on the existing lot at z=0
		LotBox existing = new LotBox(99, 99, "lane-1", AccessFace.TOP, null,
				0, 0, 0, 1, 1, 1);

		assertThat(PutawayScorer.blockingPenalty(lot, onTop, List.of(existing))).isEqualTo(1.0);
		// A far-away empty lane creates no blocking.
		assertThat(PutawayScorer.blockingPenalty(lot, bin(9, 9, 0, 1, 1, 1), List.of())).isZero();
	}

	@Test
	void lowerScoreForNearerAndLowerBin() {
		Lot lot = lot(1, 1, 1, null);
		double near = PutawayScorer.score(lot, bin(1, 0, 0, 1, 1, 1), List.of(), W);
		double far = PutawayScorer.score(lot, bin(10, 0, 0, 1, 1, 1), List.of(), W);
		assertThat(near).isLessThan(far);
	}

	// --- helpers (in-memory entities, no persistence) ---

	private static Lot lot(double w, double d, double h, LocalDate expiry) {
		Lot l = new Lot();
		l.setId(1L);
		l.setW(BigDecimal.valueOf(w));
		l.setD(BigDecimal.valueOf(d));
		l.setH(BigDecimal.valueOf(h));
		l.setWeight(BigDecimal.ONE);
		l.setExpiry(expiry);
		return l;
	}

	private static Location bin(double x, double y, double z, double w, double d, double h) {
		Location c = new Location();
		c.setId(1L);
		c.setZone("Z");
		c.setAisle("A");
		c.setRack("R");
		c.setLevel("1");
		c.setBin("B");
		c.setX(BigDecimal.valueOf(x));
		c.setY(BigDecimal.valueOf(y));
		c.setZ(BigDecimal.valueOf(z));
		c.setW(BigDecimal.valueOf(w));
		c.setD(BigDecimal.valueOf(d));
		c.setH(BigDecimal.valueOf(h));
		c.setLaneId("lane-1");
		c.setAccessFace(AccessFace.TOP);
		return c;
	}
}
