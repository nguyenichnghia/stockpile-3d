package com.stockpile.whatif.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.setup.dto.WarehouseGridSpec;
import com.stockpile.whatif.dto.PutawayWeightsDto;
import com.stockpile.whatif.dto.WhatIfPolicyResult;
import com.stockpile.whatif.dto.WhatIfResult;
import com.stockpile.whatif.service.WhatIfService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * What-if simulations (ADR-0008). POST because the spec travels in the body,
 * but both are side-effect free: nothing is written, no events published. Each
 * re-puts the lots of one warehouse (ADR-0009) — layout varies the bins, policy
 * varies the SLAP weights.
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

	@PostMapping("/api/whatif/policy")
	public WhatIfPolicyResult simulatePolicy(
			@RequestParam Long warehouseId, @Valid @RequestBody PutawayWeightsDto weights) {
		return whatIfService.simulatePolicy(warehouseId, weights);
	}
}
