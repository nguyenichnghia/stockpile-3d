package com.stockpile.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.inventory.domain.Lot;

public interface LotRepository extends JpaRepository<Lot, Long> {
}
