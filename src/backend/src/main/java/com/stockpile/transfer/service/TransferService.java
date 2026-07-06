package com.stockpile.transfer.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.domain.Movement;
import com.stockpile.inventory.domain.MovementType;
import com.stockpile.inventory.domain.Placement;
import com.stockpile.inventory.domain.Warehouse;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.PlacementRepository;
import com.stockpile.inventory.repository.WarehouseRepository;
import com.stockpile.inventory.service.MovementService;
import com.stockpile.transfer.domain.Transfer;
import com.stockpile.transfer.domain.TransferStatus;
import com.stockpile.transfer.dto.TransferDto;
import com.stockpile.transfer.repository.TransferRepository;

import lombok.RequiredArgsConstructor;

/**
 * Cross-warehouse transfer (ADR-0010, Phương án B): move a lot from warehouse A
 * to warehouse B as a linked pair of ledger entries — an OUTBOUND in A on open,
 * an INBOUND in B on receipt. Between the two the lot is in-transit (no
 * placement anywhere), and the {@link Transfer} row links the movements.
 *
 * <p>Both entries are recorded through {@link MovementService}, so each goes
 * through the normal projection, realtime push and validation. Crucially each
 * entry is a single-warehouse movement (OUTBOUND has only a from-bin in A,
 * INBOUND only a to-bin in B), so ADR-0009's "reject cross-warehouse movement"
 * rule is untouched — the two warehouses are bridged by the transfer, not by any
 * one movement.
 *
 * <p>Scan policy: these entries carry no scanRef, so a warehouse with
 * {@code require_scan} on (ADR-0007) will reject the transfer — a UI scan flow
 * for transfers is a later slice, and rejecting is the safe default.
 */
@Service
@RequiredArgsConstructor
public class TransferService {

	private final TransferRepository transferRepository;
	private final LotRepository lotRepository;
	private final LocationRepository locationRepository;
	private final WarehouseRepository warehouseRepository;
	private final PlacementRepository placementRepository;
	private final MovementService movementService;

	/**
	 * Opens a transfer of {@code lotId} to {@code toWarehouseId}: records an
	 * OUTBOUND from the lot's current bin (its source warehouse A) and creates an
	 * IN_TRANSIT transfer. The lot must be placed (a staged or already-in-transit
	 * lot cannot be transferred), and B must differ from A.
	 */
	@Transactional
	public TransferDto open(Long lotId, Long toWarehouseId) {
		Lot lot = lotRepository.findById(lotId)
				.orElseThrow(() -> new NotFoundException("Lot " + lotId + " not found"));
		Warehouse toWarehouse = warehouseRepository.findById(toWarehouseId)
				.orElseThrow(() -> new NotFoundException("Warehouse " + toWarehouseId + " not found"));

		Placement placement = placementRepository.findByLotId(lotId)
				.orElseThrow(() -> new IllegalStateException(
						"Lot " + lotId + " is not placed (staged or already in transit); nothing to transfer"));
		if (transferRepository.existsByLotIdAndStatus(lotId, TransferStatus.IN_TRANSIT)) {
			throw new IllegalStateException("Lot " + lotId + " is already in transit");
		}

		Location fromBin = placement.getBin();
		Warehouse fromWarehouse = fromBin.getWarehouse();
		if (fromWarehouse.getId().equals(toWarehouse.getId())) {
			throw new IllegalArgumentException(
					"Lot " + lotId + " is already in warehouse " + toWarehouse.getCode()
							+ "; a transfer must cross warehouses");
		}

		// OUTBOUND in A: lot leaves its bin -> in-transit (projection drops the placement).
		Movement outbound = new Movement();
		outbound.setLot(lot);
		outbound.setType(MovementType.OUTBOUND);
		outbound.setFromBin(fromBin);
		Movement savedOutbound = movementService.record(outbound);

		Transfer transfer = new Transfer();
		transfer.setLot(lot);
		transfer.setFromWarehouse(fromWarehouse);
		transfer.setToWarehouse(toWarehouse);
		transfer.setStatus(TransferStatus.IN_TRANSIT);
		transfer.setOutboundMovement(savedOutbound);
		// Map inside the transaction: open-in-view is off, so the lazy lot/sku/
		// warehouse proxies TransferDto reads must be initialized while the
		// session is open (a LazyInitializationException otherwise, unseen by the
		// @Transactional service tests but hit over the real HTTP boundary).
		return TransferDto.from(transferRepository.save(transfer));
	}

	/**
	 * Receives an in-transit transfer into {@code toBinId} of the destination:
	 * records an INBOUND that places the lot, then marks the transfer COMPLETED.
	 * The bin must belong to the transfer's destination warehouse.
	 */
	@Transactional
	public TransferDto receive(Long transferId, Long toBinId) {
		Transfer transfer = transferRepository.findById(transferId)
				.orElseThrow(() -> new NotFoundException("Transfer " + transferId + " not found"));
		if (transfer.getStatus() != TransferStatus.IN_TRANSIT) {
			throw new IllegalStateException(
					"Transfer " + transferId + " is not in transit (status " + transfer.getStatus() + ")");
		}
		Location toBin = locationRepository.findById(toBinId)
				.orElseThrow(() -> new NotFoundException("Location " + toBinId + " not found"));
		if (!toBin.getWarehouse().getId().equals(transfer.getToWarehouse().getId())) {
			throw new IllegalArgumentException(
					"Bin " + toBinId + " is in warehouse " + toBin.getWarehouse().getCode()
							+ ", not the transfer's destination " + transfer.getToWarehouse().getCode());
		}

		// INBOUND in B: lot arrives into the chosen bin (projection re-adds a placement).
		Movement inbound = new Movement();
		inbound.setLot(transfer.getLot());
		inbound.setType(MovementType.INBOUND);
		inbound.setToBin(toBin);
		Movement savedInbound = movementService.record(inbound);

		transfer.setInboundMovement(savedInbound);
		transfer.setStatus(TransferStatus.COMPLETED);
		transfer.setCompletedAt(Instant.now());
		return TransferDto.from(transfer);
	}

	/** In-transit transfers arriving at the given warehouse (the "đang chuyển" list). */
	@Transactional(readOnly = true)
	public List<TransferDto> incoming(Long toWarehouseId) {
		return transferRepository
				.findByToWarehouseIdAndStatus(toWarehouseId, TransferStatus.IN_TRANSIT)
				.stream().map(TransferDto::from).toList();
	}
}
