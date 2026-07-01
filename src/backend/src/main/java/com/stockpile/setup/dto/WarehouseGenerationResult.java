package com.stockpile.setup.dto;

/**
 * Summary returned after generating a grid warehouse: how many locations were
 * created and the grid dimensions that produced them.
 */
public record WarehouseGenerationResult(
		int locationsCreated,
		int zones,
		int aislesPerZone,
		int racksPerAisle,
		int levelsPerRack,
		int binsPerLevel) {
}
