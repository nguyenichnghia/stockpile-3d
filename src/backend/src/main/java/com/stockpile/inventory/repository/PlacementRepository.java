package com.stockpile.inventory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.inventory.domain.Placement;

public interface PlacementRepository extends JpaRepository<Placement, Long> {

	Optional<Placement> findByLotId(Long lotId);

	void deleteByLotId(Long lotId);
}
