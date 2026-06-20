package com.stockpile.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.inventory.domain.Movement;

public interface MovementRepository extends JpaRepository<Movement, Long> {
}
