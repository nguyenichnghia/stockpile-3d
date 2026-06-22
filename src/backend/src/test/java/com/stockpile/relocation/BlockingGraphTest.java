package com.stockpile.relocation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.stockpile.inventory.domain.AccessFace;
import com.stockpile.relocation.service.BlockingGraph;
import com.stockpile.relocation.service.LotBox;

/** Pure unit tests for the blocking relationship — no DB, runs in milliseconds. */
class BlockingGraphTest {

	private static LotBox box(long id, double x, double y, double z, AccessFace face) {
		// unit cube 1x1x1
		return new LotBox(id, id, "lane-1", face, null, x, y, z, 1, 1, 1);
	}

	@Test
	void boxOnTopWithOverlapBlocks() {
		LotBox bottom = box(1, 0, 0, 0, AccessFace.TOP); // z[0,1]
		LotBox flush = box(2, 0, 0, 1, AccessFace.TOP);  // z[1,2] — rests flush on bottom
		LotBox above = box(3, 0, 0, 2, AccessFace.TOP);  // z[2,3] — clearly above (gap)
		// Flush stacking blocks (b.zMin == a.zMax): the lot physically rests on it.
		assertThat(BlockingGraph.blocks(flush, bottom)).isTrue();
		assertThat(BlockingGraph.blocks(above, bottom)).isTrue();
	}

	@Test
	void sameLevelDoesNotBlock() {
		// Edge case from Dev Log: two lots at the same z (same level) never block,
		// even with '>=', because their z-ranges overlap (0 >= 1 is false).
		LotBox a = box(1, 0, 0, 0, AccessFace.TOP);
		LotBox b = box(2, 0, 0, 0, AccessFace.TOP); // same z, overlapping footprint
		assertThat(BlockingGraph.blocks(b, a)).isFalse();
		assertThat(BlockingGraph.blocks(a, b)).isFalse();
	}

	@Test
	void aboveButNoFootprintOverlapDoesNotBlock() {
		LotBox bottom = box(1, 0, 0, 0, AccessFace.TOP);
		LotBox shifted = box(2, 5, 5, 2, AccessFace.TOP); // higher but far away in (x,y)
		assertThat(BlockingGraph.blocks(shifted, bottom)).isFalse();
	}

	@Test
	void inFrontNorthBlocksAlongDepthAxis() {
		// access from NORTH -> retrieval along +y; a lot at larger y blocks.
		LotBox target = box(1, 0, 0, 0, AccessFace.NORTH);
		LotBox front = box(2, 0, 2, 0, AccessFace.NORTH); // yMin=2 > target.yMax=1, x & z overlap
		assertThat(BlockingGraph.blocks(front, target)).isTrue();
	}

	@Test
	void inFrontDifferentLaneDoesNotBlock() {
		LotBox target = box(1, 0, 0, 0, AccessFace.NORTH);
		LotBox other = new LotBox(2, 2, "lane-2", AccessFace.NORTH, null, 0, 2, 0, 1, 1, 1);
		assertThat(BlockingGraph.blocks(other, target)).isFalse();
	}

	@Test
	void blockersExcludesSelfAndReturnsOnlyBlockers() {
		LotBox target = box(1, 0, 0, 0, AccessFace.TOP);
		LotBox above = box(2, 0, 0, 2, AccessFace.TOP);
		LotBox faraway = box(3, 9, 9, 0, AccessFace.TOP);
		var blockers = BlockingGraph.blockers(target, java.util.List.of(target, above, faraway));
		assertThat(blockers).extracting(LotBox::lotId).containsExactly(2L);
	}
}
