package com.stockpile.scan.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.domain.Placement;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.scan.dto.ScanResult;

import lombok.RequiredArgsConstructor;

/**
 * Resolves a scanned barcode to warehouse state — the shared entry point for
 * every physical touchpoint (locate on the scene, confirming a pick step, and
 * later inbound/cycle-count flows). Read-only: recording what happened after a
 * scan stays on the movement ledger, with the raw code kept in {@code scanRef}.
 */
@Service
@RequiredArgsConstructor
public class ScanService {

	/** v1 lot barcode: the lot id with a fixed prefix, e.g. {@code LOT-42} (ADR-0007). */
	private static final Pattern LOT_CODE = Pattern.compile("(?i)LOT-(\\d+)");

	private final LotRepository lotRepository;
	private final LocationRepository locationRepository;
	private final PlacementRepository placementRepository;

	@Transactional(readOnly = true)
	public ScanResult resolve(String rawCode) {
		if (rawCode == null || rawCode.isBlank()) {
			throw new IllegalArgumentException("scan code must not be blank");
		}
		String code = rawCode.trim();

		Matcher lotMatcher = LOT_CODE.matcher(code);
		if (lotMatcher.matches()) {
			return resolveLot(code, Long.parseLong(lotMatcher.group(1)));
		}
		String[] parts = code.split("-");
		if (parts.length == 5) {
			return resolveBin(code, parts);
		}
		return ScanResult.notFound(code, ScanResult.Type.UNKNOWN);
	}

	private ScanResult resolveLot(String code, long lotId) {
		return lotRepository.findById(lotId)
				.map(lot -> ScanResult.lot(code, toLotInfo(lot)))
				.orElseGet(() -> ScanResult.notFound(code, ScanResult.Type.LOT));
	}

	private ScanResult.LotInfo toLotInfo(Lot lot) {
		return placementRepository.findByLotId(lot.getId())
				.map(p -> new ScanResult.LotInfo(
						lot.getId(),
						lot.getSku().getCode(),
						p.getBin().getId(),
						binCode(p.getBin()),
						p.getBin().getLaneId()))
				// A known lot that is not placed anywhere (e.g. still inbound).
				.orElseGet(() -> new ScanResult.LotInfo(
						lot.getId(), lot.getSku().getCode(), null, null, null));
	}

	private ScanResult resolveBin(String code, String[] parts) {
		return locationRepository
				.findByZoneAndAisleAndRackAndLevelAndBin(parts[0], parts[1], parts[2], parts[3], parts[4])
				.map(bin -> ScanResult.bin(code, toBinInfo(bin)))
				.orElseGet(() -> ScanResult.notFound(code, ScanResult.Type.BIN));
	}

	private ScanResult.BinInfo toBinInfo(Location bin) {
		List<Long> lotIds = placementRepository.findByBinId(bin.getId()).stream()
				.map(p -> p.getLot().getId())
				.toList();
		return new ScanResult.BinInfo(bin.getId(), binCode(bin), bin.getLaneId(), lotIds);
	}

	private static String binCode(Location l) {
		return String.join("-", l.getZone(), l.getAisle(), l.getRack(), l.getLevel(), l.getBin());
	}
}
