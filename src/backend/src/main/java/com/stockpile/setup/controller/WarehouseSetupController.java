package com.stockpile.setup.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.setup.dto.WarehouseGenerationResult;
import com.stockpile.setup.dto.WarehouseGridSpec;
import com.stockpile.setup.service.WarehouseGeneratorService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Warehouse setup: bulk-creates the {@code location} space frame. Separate from
 * inventory CRUD (day-to-day operation) — this is one-time provisioning.
 */
@RestController
@RequestMapping("/api/warehouse")
@RequiredArgsConstructor
public class WarehouseSetupController {

	private final WarehouseGeneratorService generatorService;

	/** Generates a regular grid warehouse. Only allowed when no locations exist. */
	@PostMapping("/generate")
	public ResponseEntity<WarehouseGenerationResult> generate(@Valid @RequestBody WarehouseGridSpec spec) {
		WarehouseGenerationResult result = generatorService.generate(spec);
		return ResponseEntity.status(HttpStatus.CREATED).body(result);
	}
}
