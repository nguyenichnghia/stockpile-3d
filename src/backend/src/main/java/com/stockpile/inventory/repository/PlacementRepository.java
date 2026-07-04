package com.stockpile.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.inventory.domain.Placement;

public interface PlacementRepository extends JpaRepository<Placement, Long> {

	Optional<Placement> findByLotId(Long lotId);

	void deleteByLotId(Long lotId);

	/** All placements in one warehouse (scene load, heatmap, reports, what-if). */
	List<Placement> findByBin_WarehouseId(Long warehouseId);

	/** All placements whose bin belongs to the given lane (for the blocking graph). */
	List<Placement> findByBin_WarehouseIdAndBin_LaneId(Long warehouseId, String laneId);

	/** Current placements of every lot of the given SKU code within one warehouse. */
	List<Placement> findByBin_WarehouseIdAndLot_Sku_CodeIgnoreCase(Long warehouseId, String code);

	/** Lots currently placed in the given bin (for scan resolve). */
	List<Placement> findByBinId(Long binId);
}
