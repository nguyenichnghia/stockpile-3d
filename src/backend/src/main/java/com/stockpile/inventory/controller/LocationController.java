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

import com.stockpile.inventory.dto.LocationDto;
import com.stockpile.inventory.service.LocationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

	private final LocationService locationService;

	@GetMapping
	public List<LocationDto> list() {
		return locationService.findAll();
	}

	@GetMapping("/{id}")
	public LocationDto get(@PathVariable Long id) {
		return locationService.findById(id);
	}

	@PostMapping
	public ResponseEntity<LocationDto> create(@Valid @RequestBody LocationDto dto) {
		LocationDto created = locationService.create(dto);
		return ResponseEntity.created(URI.create("/api/locations/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public LocationDto update(@PathVariable Long id, @Valid @RequestBody LocationDto dto) {
		return locationService.update(id, dto);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		locationService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
