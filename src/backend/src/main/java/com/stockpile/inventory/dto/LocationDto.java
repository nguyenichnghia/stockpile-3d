package com.stockpile.inventory.dto;

import java.math.BigDecimal;

import com.stockpile.inventory.domain.AccessFace;
import com.stockpile.inventory.domain.Location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Request/response payload for {@link Location}. */
public record LocationDto(
		Long id,
		@NotBlank String zone,
		@NotBlank String aisle,
		@NotBlank String rack,
		@NotBlank String level,
		@NotBlank String bin,
		@NotNull BigDecimal x,
		@NotNull BigDecimal y,
		@NotNull BigDecimal z,
		@NotNull @Positive BigDecimal w,
		@NotNull @Positive BigDecimal d,
		@NotNull @Positive BigDecimal h,
		@NotBlank String laneId,
		@NotNull AccessFace accessFace) {

	public static LocationDto from(Location l) {
		return new LocationDto(l.getId(), l.getZone(), l.getAisle(), l.getRack(),
				l.getLevel(), l.getBin(), l.getX(), l.getY(), l.getZ(),
				l.getW(), l.getD(), l.getH(), l.getLaneId(), l.getAccessFace());
	}

	public void applyTo(Location l) {
		l.setZone(zone);
		l.setAisle(aisle);
		l.setRack(rack);
		l.setLevel(level);
		l.setBin(bin);
		l.setX(x);
		l.setY(y);
		l.setZ(z);
		l.setW(w);
		l.setD(d);
		l.setH(h);
		l.setLaneId(laneId);
		l.setAccessFace(accessFace);
	}
}
