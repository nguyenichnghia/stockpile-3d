package com.stockpile.picking.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockpile.picking.dto.OrderDto;
import com.stockpile.picking.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	@GetMapping
	public List<OrderDto> list() {
		return orderService.findAll();
	}

	@GetMapping("/{id}")
	public OrderDto get(@PathVariable Long id) {
		return orderService.findById(id);
	}

	@PostMapping
	public ResponseEntity<OrderDto> create(@Valid @RequestBody OrderDto dto) {
		OrderDto created = orderService.create(dto);
		return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		orderService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
