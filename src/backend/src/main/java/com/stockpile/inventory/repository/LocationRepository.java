package com.stockpile.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stockpile.inventory.domain.Location;

public interface LocationRepository extends JpaRepository<Location, Long> {

	/** The bin identified by its full code parts within one warehouse (ADR-0009). */
	Optional<Location> findByWarehouseIdAndZoneAndAisleAndRackAndLevelAndBin(
			Long warehouseId, String zone, String aisle, String rack, String level, String bin);

	List<Location> findByWarehouseId(Long warehouseId);

	long countByWarehouseId(Long warehouseId);

	/** Locations in a lane that currently hold no lot (candidate temp slots). */
	@Query("""
			SELECT l FROM Location l
			WHERE l.warehouse.id = :warehouseId
			  AND l.laneId = :laneId
			  AND NOT EXISTS (SELECT p FROM Placement p WHERE p.bin = l)
			""")
	List<Location> findEmptyInLane(@Param("warehouseId") Long warehouseId, @Param("laneId") String laneId);

	/** Any location in the warehouse with no lot (fallback when the lane is full). */
	@Query("""
			SELECT l FROM Location l
			WHERE l.warehouse.id = :warehouseId
			  AND NOT EXISTS (SELECT p FROM Placement p WHERE p.bin = l)
			""")
	List<Location> findEmptyInWarehouse(@Param("warehouseId") Long warehouseId);
}
