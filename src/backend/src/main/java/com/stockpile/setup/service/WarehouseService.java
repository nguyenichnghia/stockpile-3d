package com.stockpile.setup.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Warehouse;
import com.stockpile.inventory.repository.WarehouseRepository;
import com.stockpile.setup.dto.WarehouseDto;
import com.stockpile.setup.dto.WarehousePatchDto;

import lombok.RequiredArgsConstructor;

/**
 * CRUD for warehouses (ADR-0009). Create/list only in v1 — deleting a warehouse
 * would orphan its ledger, and codes are stable identifiers like SKU codes.
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

	private final WarehouseRepository warehouseRepository;

	@Transactional(readOnly = true)
	public List<WarehouseDto> findAll() {
		return warehouseRepository.findAll().stream().map(WarehouseDto::from).toList();
	}

	@Transactional(readOnly = true)
	public WarehouseDto findById(Long id) {
		return WarehouseDto.from(get(id));
	}

	@Transactional
	public WarehouseDto create(WarehouseDto dto) {
		warehouseRepository.findByCode(dto.code()).ifPresent(w -> {
			throw new IllegalArgumentException("Warehouse code already exists: " + dto.code());
		});
		Warehouse warehouse = new Warehouse();
		warehouse.setCode(dto.code());
		warehouse.setName(dto.name());
		warehouse.setRequireScan(Boolean.TRUE.equals(dto.requireScan()));
		return WarehouseDto.from(warehouseRepository.save(warehouse));
	}

	@Transactional
	public WarehouseDto update(Long id, WarehousePatchDto patch) {
		Warehouse warehouse = get(id);
		if (patch.requireScan() != null) {
			warehouse.setRequireScan(patch.requireScan());
		}
		return WarehouseDto.from(warehouse);
	}

	Warehouse get(Long id) {
		return warehouseRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Warehouse " + id + " not found"));
	}
}
