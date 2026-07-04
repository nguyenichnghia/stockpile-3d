package com.stockpile.putaway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.putaway.dto.PutawaySuggestion;
import com.stockpile.putaway.service.PutawayService;

import lombok.RequiredArgsConstructor;

/**
 * Putaway suggestions (SLAP). Read-only: recommends where to store a lot but
 * does not place it — putaway is a separate, confirmed action.
 */
@RestController
@RequiredArgsConstructor
public class PutawayController {

	private final PutawayService putawayService;

	@GetMapping("/api/putaway-suggestion")
	public PutawaySuggestion suggest(@RequestParam long lotId, @RequestParam long warehouseId) {
		return putawayService.suggest(lotId, warehouseId);
	}
}
