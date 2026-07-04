package com.stockpile.reporting.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.reporting.dto.MovementDaily;
import com.stockpile.reporting.dto.ReportSummary;
import com.stockpile.reporting.service.ReportingService;

import lombok.RequiredArgsConstructor;

/** Management dashboard aggregates. Read-only. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportingController {

	private final ReportingService reportingService;

	@GetMapping("/summary")
	public ReportSummary summary(@RequestParam Long warehouseId) {
		return reportingService.summary(warehouseId);
	}

	@GetMapping("/movements")
	public List<MovementDaily> movements(
			@RequestParam(required = false) Integer days, @RequestParam Long warehouseId) {
		return reportingService.movementsDaily(days, warehouseId);
	}
}
