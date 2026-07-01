package com.stockpile.locate.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.locate.dto.BinLocateResult;
import com.stockpile.locate.dto.LocateResult;
import com.stockpile.locate.service.LocateService;

import lombok.RequiredArgsConstructor;

/**
 * Locate/search endpoints for the 3D scene: find the bins to highlight for a
 * SKU or a bin code. Read-only — presents state, never changes it.
 */
@RestController
@RequiredArgsConstructor
public class LocateController {

	private final LocateService locateService;

	@GetMapping("/api/lots/locate")
	public LocateResult locate(@RequestParam String sku) {
		return locateService.locateBySku(sku);
	}

	@GetMapping("/api/locations/locate")
	public BinLocateResult locateBin(@RequestParam String code) {
		return locateService.locateByBinCode(code);
	}
}
