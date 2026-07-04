package com.stockpile.picking.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.picking.domain.OrderStatus;
import com.stockpile.picking.domain.PickOrder;

public interface PickOrderRepository extends JpaRepository<PickOrder, Long> {

	/** Orders of one warehouse in one lifecycle state (e.g. OPEN backlog, for reporting). */
	long countByWarehouseIdAndStatus(Long warehouseId, OrderStatus status);
}
