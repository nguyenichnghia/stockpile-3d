package com.stockpile.picking.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.stockpile.inventory.domain.Warehouse;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A request to retrieve stock: a header plus one line per SKU. The picking
 * engine reads an order to plan a pick-list; it never mutates the order state
 * as part of planning (proposal-only, like the other engines).
 */
@Entity
@Table(name = "pick_order")
@Getter
@Setter
public class PickOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String code;

	/** The warehouse this order is fulfilled from (ADR-0009). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "warehouse_id", nullable = false)
	private Warehouse warehouse;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status = OrderStatus.OPEN;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderLine> lines = new ArrayList<>();

	/** Adds a line and wires the back-reference. */
	public void addLine(OrderLine line) {
		line.setOrder(this);
		lines.add(line);
	}
}
