package com.stockpile.picking.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.picking.domain.PickOrder;

public interface PickOrderRepository extends JpaRepository<PickOrder, Long> {
}
