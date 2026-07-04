package com.stockpile.inventory.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.stockpile.inventory.domain.Movement;

public interface MovementRepository extends JpaRepository<Movement, Long> {

	/** Ledger order for replay: chronological, id as tiebreaker for equal ts. */
	List<Movement> findAllByOrderByTsAscIdAsc();

	/** Movements of one warehouse recorded at or after the instant (e.g. "today"). */
	long countByWarehouseIdAndTsGreaterThanEqual(Long warehouseId, Instant from);

	/** One row per (UTC day, movement type) since {@code from}, for one warehouse. */
	@Query(value = """
			select cast(m.ts as date) as day, m.type as type, count(*) as cnt
			from movement m
			where m.warehouse_id = :warehouseId
			  and m.ts >= :from
			group by day, m.type
			order by day
			""", nativeQuery = true)
	List<DailyTypeCount> countDailyByType(Long warehouseId, Instant from);

	/** Projection for {@link #countDailyByType}. */
	interface DailyTypeCount {
		LocalDate getDay();
		String getType();
		long getCnt();
	}
}
