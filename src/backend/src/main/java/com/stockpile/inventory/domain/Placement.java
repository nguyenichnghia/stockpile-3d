package com.stockpile.inventory.domain;

import java.math.BigDecimal;

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
 * Which bin a lot currently occupies. This is a projection rebuilt from the
 * movement ledger (app-maintained); the ledger remains the source of truth.
 */
@Entity
@Table(name = "placement")
@Getter
@Setter
public class Placement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** A lot occupies at most one location at a time (unique). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lot_id", nullable = false, unique = true)
	private Lot lot;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bin_id", nullable = false)
	private Location bin;

	/** Pose of the lot within the bin. */
	@Column(nullable = false)
	private BigDecimal x;

	@Column(nullable = false)
	private BigDecimal y;

	@Column(nullable = false)
	private BigDecimal z;
}
