package com.stockpile.inventory.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.inventory.dto.MovementDto;
import com.stockpile.inventory.service.MovementService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Records ledger entries. Append-only: only POST is exposed. Recording a
 * movement also updates the placement projection.
 */
@RestController
@RequestMapping("/api/movements")
@RequiredArgsConstructor
public class MovementController {

	private final MovementService movementService;

	@PostMapping
	public ResponseEntity<MovementDto> record(@Valid @RequestBody MovementDto dto) {
		MovementDto created = MovementDto.from(movementService.record(dto));
		return ResponseEntity.created(URI.create("/api/movements/" + created.id())).body(created);
	}
}
