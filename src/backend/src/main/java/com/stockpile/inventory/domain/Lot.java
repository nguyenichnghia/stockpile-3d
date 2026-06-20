package com.stockpile.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

/** A physical unit of a SKU placed in the warehouse. */
@Entity
@Table(name = "lot")
@Getter
@Setter
public class Lot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sku_id", nullable = false)
	private Sku sku;

	/** Bounding box of this lot: width, depth, height. */
	@Column(nullable = false)
	private BigDecimal w;

	@Column(nullable = false)
	private BigDecimal d;

	@Column(nullable = false)
	private BigDecimal h;

	@Column(nullable = false)
	private BigDecimal weight;

	private LocalDate expiry;

	@Column(name = "predicted_retrieval_at")
	private Instant predictedRetrievalAt;
}
