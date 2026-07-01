package com.stockpile.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockpile.inventory.domain.AccessFace;
import com.stockpile.relocation.dto.RelocationStep;
import com.stockpile.relocation.service.RelocationPlanner.EmptySlot;

/** Pure unit tests for the greedy CRP planner — no DB, runs in milliseconds. */
class RelocationPlannerTest {

	@Test
	void freeLotNeedsNoMoves() {
		LotBox target = box(1, 100, 0, 0, 0);
		List<LotBox> lane = new ArrayList<>(List.of(target));

		List<RelocationStep> steps = RelocationPlanner.plan(target, lane, List.of(slot(200, 5, 5, 0)));

		assertThat(steps).isEmpty();
	}

	@Test
	void stackedLotIsFreedByMovingTheTopOne() {
		// bottom (z=0) blocked by top (z=2), same (x,y); one empty temp slot.
		LotBox bottom = box(1, 100, 0, 0, 0);
		LotBox top = box(2, 101, 0, 0, 2);
		List<LotBox> lane = new ArrayList<>(List.of(bottom, top));

		List<RelocationStep> steps = RelocationPlanner.plan(bottom, lane, List.of(slot(200, 5, 5, 0)));

		assertThat(steps).hasSize(1);
		assertThat(steps.get(0).lotId()).isEqualTo(2);      // the top lot moves
		assertThat(steps.get(0).fromBinId()).isEqualTo(101);
		assertThat(steps.get(0).toBinId()).isEqualTo(200);  // into the empty slot
	}

	@Test
	void throwsWhenNoTempSlotAvailable() {
		LotBox bottom = box(1, 100, 0, 0, 0);
		LotBox top = box(2, 101, 0, 0, 2);
		List<LotBox> lane = new ArrayList<>(List.of(bottom, top));

		// No empty slots at all -> cannot move the blocker.
		assertThatThrownBy(() -> RelocationPlanner.plan(bottom, lane, List.of()))
				.isInstanceOf(IllegalStateException.class);
	}

	// --- helpers (unit cubes, no persistence) ---

	private static LotBox box(long lotId, long binId, double x, double y, double z) {
		return new LotBox(lotId, binId, "lane-1", AccessFace.TOP, null, x, y, z, 1, 1, 1);
	}

	private static EmptySlot slot(long binId, double x, double y, double z) {
		return new EmptySlot(binId, "lane-1", AccessFace.TOP, x, y, z);
	}
}
