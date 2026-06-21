package com.stockpile.inventory.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Sku;
import com.stockpile.inventory.dto.SkuDto;
import com.stockpile.inventory.repository.SkuRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SkuService {

	private final SkuRepository skuRepository;

	@Transactional(readOnly = true)
	public List<SkuDto> findAll() {
		return skuRepository.findAll().stream().map(SkuDto::from).toList();
	}

	@Transactional(readOnly = true)
	public SkuDto findById(Long id) {
		return SkuDto.from(get(id));
	}

	@Transactional
	public SkuDto create(SkuDto dto) {
		Sku sku = new Sku();
		dto.applyTo(sku);
		return SkuDto.from(skuRepository.save(sku));
	}

	@Transactional
	public SkuDto update(Long id, SkuDto dto) {
		Sku sku = get(id);
		dto.applyTo(sku);
		return SkuDto.from(skuRepository.save(sku));
	}

	@Transactional
	public void delete(Long id) {
		skuRepository.delete(get(id));
	}

	private Sku get(Long id) {
		return skuRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Sku " + id + " not found"));
	}
}
