package com.stockpile.transfer.domain;

import java.time.Instant;

import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.domain.Movement;
import com.stockpile.inventory.domain.Warehouse;

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
 * Links the two ledger entries of a cross-warehouse transfer (ADR-0010,
 * Phương án B): an OUTBOUND in the source warehouse and, on arrival, an INBOUND
 * in the destination. This row is <em>not</em> the source of truth for the lot's
 * position — that stays derived from the movement ledger. It only ties the two
 * movements together and carries the trip's state.
 *
 * <p>While {@link TransferStatus#IN_TRANSIT} the lot has left A (its OUTBOUND
 * removed the placement) and has no placement anywhere — the same "exists but
 * unplaced" state as a lot staged after INBOUND.
 */
@Entity
@Table(name = "transfer")
@Getter
@Setter
public class Transfer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lot_id", nullable = false)
	private Lot lot;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "from_warehouse_id", nullable = false)
	private Warehouse fromWarehouse;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "to_warehouse_id", nullable = false)
	private Warehouse toWarehouse;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransferStatus status = TransferStatus.IN_TRANSIT;

	/** The OUTBOUND ledger entry in the source warehouse; set when the transfer opens. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "outbound_movement_id", nullable = false)
	private Movement outboundMovement;

	/** The INBOUND ledger entry in the destination; null until the goods are received. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "inbound_movement_id")
	private Movement inboundMovement;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "completed_at")
	private Instant completedAt;
}
