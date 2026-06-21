package com.stockpile.inventory.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpile.common.NotFoundException;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.domain.Sku;
import com.stockpile.inventory.dto.LotDto;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.SkuRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LotService {

	private final LotRepository lotRepository;
	private final SkuRepository skuRepository;

	@Transactional(readOnly = true)
	public List<LotDto> findAll() {
		return lotRepository.findAll().stream().map(LotDto::from).toList();
	}

	@Transactional(readOnly = true)
	public LotDto findById(Long id) {
		return LotDto.from(get(id));
	}

	@Transactional
	public LotDto create(LotDto dto) {
		Lot lot = new Lot();
		applyTo(lot, dto);
		return LotDto.from(lotRepository.save(lot));
	}

	@Transactional
	public LotDto update(Long id, LotDto dto) {
		Lot lot = get(id);
		applyTo(lot, dto);
		return LotDto.from(lotRepository.save(lot));
	}

	@Transactional
	public void delete(Long id) {
		lotRepository.delete(get(id));
	}

	private void applyTo(Lot lot, LotDto dto) {
		Sku sku = skuRepository.findById(dto.skuId())
				.orElseThrow(() -> new NotFoundException("Sku " + dto.skuId() + " not found"));
		lot.setSku(sku);
		lot.setW(dto.w());
		lot.setD(dto.d());
		lot.setH(dto.h());
		lot.setWeight(dto.weight());
		lot.setExpiry(dto.expiry());
		lot.setPredictedRetrievalAt(dto.predictedRetrievalAt());
	}

	private Lot get(Long id) {
		return lotRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Lot " + id + " not found"));
	}
}
