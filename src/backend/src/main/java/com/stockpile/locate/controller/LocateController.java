package com.stockpile.locate.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.locate.dto.LocateResult;
import com.stockpile.locate.service.LocateService;

import lombok.RequiredArgsConstructor;

/**
 * Locate/search endpoint for the 3D scene: given a SKU code, returns the
 * placements to highlight. Read-only — presents state, never changes it.
 */
@RestController
@RequiredArgsConstructor
public class LocateController {

	private final LocateService locateService;

	@GetMapping("/api/lots/locate")
	public LocateResult locate(@RequestParam String sku) {
		return locateService.locateBySku(sku);
	}
}
