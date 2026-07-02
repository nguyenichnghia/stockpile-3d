package com.stockpile.picking.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.picking.dto.PickPlan;
import com.stockpile.picking.service.PickingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pick-plan")
@RequiredArgsConstructor
public class PickPlanController {

	private final PickingService pickingService;

	/** Proposes a pick-list for an order (relocations interleaved with picks). */
	@GetMapping
	public PickPlan plan(@RequestParam long orderId) {
		return pickingService.plan(orderId);
	}
}
