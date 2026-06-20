package com.stockpile.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.inventory.domain.Sku;

public interface SkuRepository extends JpaRepository<Sku, Long> {
}
