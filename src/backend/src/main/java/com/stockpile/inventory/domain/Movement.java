package com.stockpile.inventory.domain;

import java.time.Instant;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Append-only physical ledger entry: the source of truth for warehouse state.
 * Entries are inserted, never updated or deleted.
 */
@Entity
@Table(name = "movement")
@Getter
@Setter
public class Movement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lot_id", nullable = false)
	private Lot lot;

	/**
	 * Where the event happened. Derived from the bins when present; a movement
	 * with no bins (INBOUND to staging) must state it explicitly (ADR-0009).
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "warehouse_id", nullable = false)
	private Warehouse warehouse;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MovementType type;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "from_bin")
	private Location fromBin;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "to_bin")
	private Location toBin;

	@Column(nullable = false)
	private Instant ts;

	private String actor;

	@Column(name = "scan_ref")
	private String scanRef;
}
