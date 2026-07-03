package com.stockpile.picking.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.picking.domain.OrderStatus;
import com.stockpile.picking.domain.PickOrder;

public interface PickOrderRepository extends JpaRepository<PickOrder, Long> {

	/** Orders in one lifecycle state (e.g. how many are still OPEN, for reporting). */
	long countByStatus(OrderStatus status);
}
