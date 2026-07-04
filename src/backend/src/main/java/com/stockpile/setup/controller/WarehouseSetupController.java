package com.stockpile.setup.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.setup.dto.WarehouseDto;
import com.stockpile.setup.dto.WarehouseGenerationResult;
import com.stockpile.setup.dto.WarehouseGridSpec;
import com.stockpile.setup.service.WarehouseGeneratorService;
import com.stockpile.setup.service.WarehouseService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Warehouse setup: registering warehouses and bulk-creating each one's
 * {@code location} space frame (ADR-0009). Separate from inventory CRUD
 * (day-to-day operation) — this is provisioning.
 */
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseSetupController {

	private final WarehouseService warehouseService;
	private final WarehouseGeneratorService generatorService;

	@GetMapping
	public List<WarehouseDto> list() {
		return warehouseService.findAll();
	}

	@GetMapping("/{id}")
	public WarehouseDto get(@PathVariable Long id) {
		return warehouseService.findById(id);
	}

	@PostMapping
	public ResponseEntity<WarehouseDto> create(@Valid @RequestBody WarehouseDto dto) {
		WarehouseDto created = warehouseService.create(dto);
		return ResponseEntity.created(URI.create("/api/warehouses/" + created.id())).body(created);
	}

	/** Generates a regular grid for one warehouse. Only allowed while it has no locations. */
	@PostMapping("/{id}/generate")
	public ResponseEntity<WarehouseGenerationResult> generate(
			@PathVariable Long id, @Valid @RequestBody WarehouseGridSpec spec) {
		WarehouseGenerationResult result = generatorService.generate(id, spec);
		return ResponseEntity.status(HttpStatus.CREATED).body(result);
	}
}
