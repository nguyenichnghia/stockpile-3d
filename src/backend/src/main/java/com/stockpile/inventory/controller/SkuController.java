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

import com.stockpile.inventory.dto.SkuDto;
import com.stockpile.inventory.service.SkuService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/skus")
@RequiredArgsConstructor
public class SkuController {

	private final SkuService skuService;

	@GetMapping
	public List<SkuDto> list() {
		return skuService.findAll();
	}

	@GetMapping("/{id}")
	public SkuDto get(@PathVariable Long id) {
		return skuService.findById(id);
	}

	@PostMapping
	public ResponseEntity<SkuDto> create(@Valid @RequestBody SkuDto dto) {
		SkuDto created = skuService.create(dto);
		return ResponseEntity.created(URI.create("/api/skus/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public SkuDto update(@PathVariable Long id, @Valid @RequestBody SkuDto dto) {
		return skuService.update(id, dto);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		skuService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
