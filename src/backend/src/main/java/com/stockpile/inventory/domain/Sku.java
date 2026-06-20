package com.stockpile.inventory.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Product master. */
@Entity
@Table(name = "sku")
@Getter
@Setter
public class Sku {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String code;

	@Column(nullable = false)
	private String name;

	/** Default dimensions: width, depth, height. */
	@Column(nullable = false)
	private BigDecimal w;

	@Column(nullable = false)
	private BigDecimal d;

	@Column(nullable = false)
	private BigDecimal h;

	@Column(nullable = false)
	private BigDecimal weight;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private HandlingType handling;
}
