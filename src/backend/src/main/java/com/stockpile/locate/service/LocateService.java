package com.stockpile.locate.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.inventory.domain.Placement;
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.locate.dto.LocateResult;
import com.stockpile.locate.dto.LocateResult.Match;

import lombok.RequiredArgsConstructor;

/**
 * Locate/search: finds where every lot of a given SKU currently sits, so the 3D
 * scene can highlight those bins. A SKU may have many lots in many bins, so the
 * result is a list. Read-only.
 */
@Service
@RequiredArgsConstructor
public class LocateService {

	private final PlacementRepository placementRepository;

	@Transactional(readOnly = true)
	public LocateResult locateBySku(String code) {
		if (code == null || code.isBlank()) {
			throw new IllegalArgumentException("sku code must not be blank");
		}
		List<Match> matches = placementRepository.findByLot_Sku_CodeIgnoreCase(code.trim()).stream()
				.map(LocateService::toMatch)
				.toList();
		return new LocateResult(code.trim(), matches.size(), matches);
	}

	private static Match toMatch(Placement p) {
		return new Match(p.getLot().getId(), p.getBin().getId(), p.getX(), p.getY(), p.getZ());
	}
}
