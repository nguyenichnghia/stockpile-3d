package com.stockpile.inventory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.inventory.domain.Warehouse;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

	Optional<Warehouse> findByCode(String code);
}
