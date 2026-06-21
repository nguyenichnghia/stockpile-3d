package com.stockpile.inventory.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.inventory.dto.LotDto;
import com.stockpile.inventory.service.LotService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/lots")
@RequiredArgsConstructor
public class LotController {

	private final LotService lotService;

	@GetMapping
	public List<LotDto> list() {
		return lotService.findAll();
	}

	@GetMapping("/{id}")
	public LotDto get(@PathVariable Long id) {
		return lotService.findById(id);
	}

	@PostMapping
	public ResponseEntity<LotDto> create(@Valid @RequestBody LotDto dto) {
		LotDto created = lotService.create(dto);
		return ResponseEntity.created(URI.create("/api/lots/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public LotDto update(@PathVariable Long id, @Valid @RequestBody LotDto dto) {
		return lotService.update(id, dto);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		lotService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
