package com.stockpile.inventory.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.dto.LocationDto;
import com.stockpile.inventory.repository.LocationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LocationService {

	private final LocationRepository locationRepository;

	@Transactional(readOnly = true)
	public List<LocationDto> findAll() {
		return locationRepository.findAll().stream().map(LocationDto::from).toList();
	}

	@Transactional(readOnly = true)
	public LocationDto findById(Long id) {
		return LocationDto.from(get(id));
	}

	@Transactional
	public LocationDto create(LocationDto dto) {
		Location location = new Location();
		dto.applyTo(location);
		return LocationDto.from(locationRepository.save(location));
	}

	@Transactional
	public LocationDto update(Long id, LocationDto dto) {
		Location location = get(id);
		dto.applyTo(location);
		return LocationDto.from(locationRepository.save(location));
	}

	@Transactional
	public void delete(Long id) {
		locationRepository.delete(get(id));
	}

	private Location get(Long id) {
		return locationRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Location " + id + " not found"));
	}
}
