package com.stockpile.inventory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.inventory.dto.PlacementDto;
import com.stockpile.inventory.repository.PlacementRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read-only access to the placement projection (current warehouse state),
 * intended for the 3D scene. Mutations happen through the movement ledger.
 */
@RestController
@RequestMapping("/api/placements")
@RequiredArgsConstructor
public class PlacementController {

	private final PlacementRepository placementRepository;

	@GetMapping
	public List<PlacementDto> list(@RequestParam Long warehouseId) {
		return placementRepository.findByBin_WarehouseId(warehouseId).stream()
				.map(PlacementDto::from)
				.toList();
	}
}
