package com.stockpile.transfer.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.transfer.dto.OpenTransferRequest;
import com.stockpile.transfer.dto.ReceiveTransferRequest;
import com.stockpile.transfer.dto.TransferDto;
import com.stockpile.transfer.service.TransferService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Cross-warehouse transfer (ADR-0010). Two explicit steps, mirroring the ledger:
 * open (records the OUTBOUND in the source) and receive (records the INBOUND in
 * the destination). Listing shows what is arriving at a warehouse.
 */
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

	private final TransferService transferService;

	/** Incoming (in-transit) transfers for a destination warehouse. */
	@GetMapping
	public List<TransferDto> incoming(@RequestParam Long toWarehouseId) {
		return transferService.incoming(toWarehouseId);
	}

	@PostMapping
	public ResponseEntity<TransferDto> open(@Valid @RequestBody OpenTransferRequest req) {
		TransferDto dto = transferService.open(req.lotId(), req.toWarehouseId());
		return ResponseEntity.created(URI.create("/api/transfers/" + dto.id())).body(dto);
	}

	@PostMapping("/{id}/receive")
	public TransferDto receive(@PathVariable Long id, @Valid @RequestBody ReceiveTransferRequest req) {
		return transferService.receive(id, req.toBinId());
	}
}
