package com.stockpile.locate.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Placement;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.locate.dto.BinLocateResult;
import com.stockpile.locate.dto.LocateResult;
import com.stockpile.locate.dto.LocateResult.Match;

import lombok.RequiredArgsConstructor;

/**
 * Locate/search: finds where stock or a bin sits so the 3D scene can highlight
 * it. Locating a SKU may return many placements; locating a bin returns one bin
 * (possibly empty). Read-only.
 */
@Service
@RequiredArgsConstructor
public class LocateService {

	private final PlacementRepository placementRepository;
	private final LocationRepository locationRepository;

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

	/**
	 * Locates a bin by its full code {@code zone-aisle-rack-level-bin}. Returns a
	 * result with {@code found=false} when no such bin exists (an empty bin still
	 * counts as found). Highlights the bin frame even if it holds no lot.
	 */
	@Transactional(readOnly = true)
	public BinLocateResult locateByBinCode(String code) {
		if (code == null || code.isBlank()) {
			throw new IllegalArgumentException("bin code must not be blank");
		}
		String[] parts = code.trim().split("-");
		if (parts.length != 5) {
			throw new IllegalArgumentException(
					"bin code must be zone-aisle-rack-level-bin, got: " + code);
		}
		return locationRepository
				.findByZoneAndAisleAndRackAndLevelAndBin(parts[0], parts[1], parts[2], parts[3], parts[4])
				.map(LocateService::toBinResult)
				.orElseGet(() -> BinLocateResult.notFound(code.trim()));
	}

	private static Match toMatch(Placement p) {
		return new Match(p.getLot().getId(), p.getBin().getId(), p.getX(), p.getY(), p.getZ());
	}

	private static BinLocateResult toBinResult(Location l) {
		return new BinLocateResult(
				String.join("-", l.getZone(), l.getAisle(), l.getRack(), l.getLevel(), l.getBin()),
				true, l.getId(), l.getX(), l.getY(), l.getZ());
	}
}
