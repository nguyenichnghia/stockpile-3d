package com.stockpile.scan.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.scan.dto.ScanResult;
import com.stockpile.scan.service.ScanService;

import lombok.RequiredArgsConstructor;

/**
 * Resolves a scanned barcode ({@code LOT-{id}} or a full bin code) to the lot
 * or bin it names. Read-only — what happens after the scan (confirming a step,
 * locating on the scene) is the caller's decision.
 */
@RestController
@RequiredArgsConstructor
public class ScanController {

	private final ScanService scanService;

	@GetMapping("/api/scan")
	public ScanResult resolve(@RequestParam String code, @RequestParam Long warehouseId) {
		return scanService.resolve(code, warehouseId);
	}
}
