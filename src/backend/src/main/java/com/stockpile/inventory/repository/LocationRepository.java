package com.stockpile.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.inventory.domain.Location;

public interface LocationRepository extends JpaRepository<Location, Long> {
}
