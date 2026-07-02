package com.stockpile.picking.domain;

import com.stockpile.inventory.domain.Sku;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One SKU and quantity within an order. {@code qty} counts crates/boxes/pallets
 * (the unit the warehouse tracks), not individual items.
 */
@Entity
@Table(name = "order_line")
@Getter
@Setter
public class OrderLine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private PickOrder order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sku_id", nullable = false)
	private Sku sku;

	@Column(nullable = false)
	private int qty;
}
