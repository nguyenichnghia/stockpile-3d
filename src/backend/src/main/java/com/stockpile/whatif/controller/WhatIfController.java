package com.stockpile.whatif.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.setup.dto.WarehouseGridSpec;
import com.stockpile.whatif.dto.WhatIfResult;
import com.stockpile.whatif.service.WhatIfService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Layout what-if. POST because the grid spec travels in the body, but the
 * simulation is side-effect free: nothing is written, no events published.
 * The simulation re-puts the lots of one warehouse (ADR-0009).
 */
@RestController
@RequiredArgsConstructor
public class WhatIfController {

	private final WhatIfService whatIfService;

	@PostMapping("/api/whatif/layout")
	public WhatIfResult simulate(
			@RequestParam Long warehouseId, @Valid @RequestBody WarehouseGridSpec spec) {
		return whatIfService.simulate(warehouseId, spec);
	}
}
