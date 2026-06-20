package com.stockpile.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.inventory.domain.Movement;

public interface MovementRepository extends JpaRepository<Movement, Long> {

	/** Ledger order for replay: chronological, id as tiebreaker for equal ts. */
	List<Movement> findAllByOrderByTsAscIdAsc();
}
