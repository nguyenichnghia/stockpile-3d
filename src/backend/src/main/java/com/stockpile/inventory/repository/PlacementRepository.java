package com.stockpile.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.inventory.domain.Placement;

public interface PlacementRepository extends JpaRepository<Placement, Long> {

	Optional<Placement> findByLotId(Long lotId);

	void deleteByLotId(Long lotId);

	/** All placements whose bin belongs to the given lane (for the blocking graph). */
	List<Placement> findByBin_LaneId(String laneId);

	/** Current placements of every lot of the given SKU code (for locate/search). */
	List<Placement> findByLot_Sku_CodeIgnoreCase(String code);
}
