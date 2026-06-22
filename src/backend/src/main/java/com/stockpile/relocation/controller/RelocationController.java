package com.stockpile.relocation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.relocation.dto.RelocationPlan;
import com.stockpile.relocation.service.RelocationService;

import lombok.RequiredArgsConstructor;

/**
 * Exposes the Relocation Engine. Returns a proposed plan only; the user
 * confirms before any move is recorded.
 */
@RestController
@RequestMapping("/api/relocation-plan")
@RequiredArgsConstructor
public class RelocationController {

	private final RelocationService relocationService;

	@GetMapping
	public RelocationPlan plan(@RequestParam Long lotId) {
		return relocationService.plan(lotId);
	}
}
