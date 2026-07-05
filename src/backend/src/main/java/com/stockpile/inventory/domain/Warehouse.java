package com.stockpile.inventory.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A physical warehouse site (ADR-0009). Each warehouse has its own local
 * coordinate system with the dock at the origin (0,0,0); locations, ledger
 * entries and pick orders all belong to exactly one warehouse. v1 has no
 * cross-warehouse transfers.
 */
@Entity
@Table(name = "warehouse")
@Getter
@Setter
public class Warehouse {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String code;

	@Column(nullable = false)
	private String name;

	/**
	 * Scan-enforcement policy (ADR-0007's follow-up slice): when on, a movement
	 * in this warehouse must carry a scanRef matching its lot barcode; when off
	 * the ledger merely records what was scanned (encourage + audit).
	 */
	@Column(name = "require_scan", nullable = false)
	private boolean requireScan = false;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();
}
