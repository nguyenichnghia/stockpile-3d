package com.stockpile.picking.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Sku;
import com.stockpile.inventory.repository.SkuRepository;
import com.stockpile.inventory.repository.WarehouseRepository;
import com.stockpile.picking.domain.OrderLine;
import com.stockpile.picking.domain.PickOrder;
import com.stockpile.picking.dto.OrderDto;
import com.stockpile.picking.dto.OrderLineDto;
import com.stockpile.picking.repository.PickOrderRepository;

import lombok.RequiredArgsConstructor;

/** CRUD for pick orders. Planning lives in {@link PickingService}. */
@Service
@RequiredArgsConstructor
public class OrderService {

	private final PickOrderRepository orderRepository;
	private final SkuRepository skuRepository;
	private final WarehouseRepository warehouseRepository;

	@Transactional(readOnly = true)
	public List<OrderDto> findAll() {
		return orderRepository.findAll().stream().map(OrderDto::from).toList();
	}

	@Transactional(readOnly = true)
	public OrderDto findById(Long id) {
		return OrderDto.from(get(id));
	}

	@Transactional
	public OrderDto create(OrderDto dto) {
		PickOrder order = new PickOrder();
		order.setCode(dto.code());
		order.setWarehouse(warehouseRepository.findById(dto.warehouseId())
				.orElseThrow(() -> new NotFoundException("Warehouse " + dto.warehouseId() + " not found")));
		for (OrderLineDto lineDto : dto.lines()) {
			order.addLine(newLine(lineDto));
		}
		return OrderDto.from(orderRepository.save(order));
	}

	@Transactional
	public void delete(Long id) {
		orderRepository.delete(get(id));
	}

	private OrderLine newLine(OrderLineDto dto) {
		OrderLine line = new OrderLine();
		line.setSku(sku(dto.skuId()));
		line.setQty(dto.qty());
		return line;
	}

	private Sku sku(Long id) {
		return skuRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Sku " + id + " not found"));
	}

	PickOrder get(Long id) {
		return orderRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
	}
}
