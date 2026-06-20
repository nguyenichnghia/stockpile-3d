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

/** A warehouse slot: zone -> aisle -> rack -> level -> bin. */
@Entity
@Table(name = "location")
@Getter
@Setter
public class Location {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String zone;

	@Column(nullable = false)
	private String aisle;

	@Column(nullable = false)
	private String rack;

	@Column(nullable = false)
	private String level;

	@Column(nullable = false)
	private String bin;

	/** Corner coordinates of the slot. */
	@Column(nullable = false)
	private BigDecimal x;

	@Column(nullable = false)
	private BigDecimal y;

	@Column(nullable = false)
	private BigDecimal z;

	/** Slot dimensions: width, depth, height. */
	@Column(nullable = false)
	private BigDecimal w;

	@Column(nullable = false)
	private BigDecimal d;

	@Column(nullable = false)
	private BigDecimal h;

	@Column(name = "lane_id", nullable = false)
	private String laneId;

	@Enumerated(EnumType.STRING)
	@Column(name = "access_face", nullable = false)
	private AccessFace accessFace;
}
