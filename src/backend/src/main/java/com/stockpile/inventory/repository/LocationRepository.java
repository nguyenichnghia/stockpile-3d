package com.stockpile.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stockpile.inventory.domain.Location;

public interface LocationRepository extends JpaRepository<Location, Long> {

	/** Locations in a lane that currently hold no lot (candidate temp slots). */
	@Query("""
			SELECT l FROM Location l
			WHERE l.laneId = :laneId
			  AND NOT EXISTS (SELECT p FROM Placement p WHERE p.bin = l)
			""")
	List<Location> findEmptyInLane(@Param("laneId") String laneId);

	/** Any location with no lot (fallback when the lane is full). */
	@Query("""
			SELECT l FROM Location l
			WHERE NOT EXISTS (SELECT p FROM Placement p WHERE p.bin = l)
			""")
	List<Location> findEmpty();
}
