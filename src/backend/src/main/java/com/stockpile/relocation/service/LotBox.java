package com.stockpile.relocation.service;

import java.time.Instant;

import com.stockpile.inventory.domain.AccessFace;

/**
 * Lightweight axis-aligned box for one placed lot, used by the blocking graph.
 * Decoupled from JPA entities so the algorithm is pure and easy to test.
 *
 * <p>Coordinates: x = width axis, y = depth axis, z = height. The box spans
 * [x, x+w] x [y, y+d] x [z, z+h]. Pose and bin are mutable so the greedy loop
 * can simulate a relocation in memory.
 */
public final class LotBox {

	private final long lotId;
	private final Instant predictedRetrievalAt;
	private final double w;
	private final double d;
	private final double h;

	private long binId;
	private String laneId;
	private AccessFace accessFace;
	private double x;
	private double y;
	private double z;

	public LotBox(long lotId, long binId, String laneId, AccessFace accessFace,
			Instant predictedRetrievalAt, double x, double y, double z,
			double w, double d, double h) {
		this.lotId = lotId;
		this.binId = binId;
		this.laneId = laneId;
		this.accessFace = accessFace;
		this.predictedRetrievalAt = predictedRetrievalAt;
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
		this.d = d;
		this.h = h;
	}

	public long lotId() { return lotId; }
	public long binId() { return binId; }
	public String laneId() { return laneId; }
	public AccessFace accessFace() { return accessFace; }
	public Instant predictedRetrievalAt() { return predictedRetrievalAt; }
	public double width() { return w; }
	public double depth() { return d; }
	public double height() { return h; }

	public double xMin() { return x; }
	public double xMax() { return x + w; }
	public double yMin() { return y; }
	public double yMax() { return y + d; }
	public double zMin() { return z; }
	public double zMax() { return z + h; }

	/** Simulate moving this lot into a new slot (pose + bin/lane/face). */
	public void relocateTo(long newBinId, String newLaneId, AccessFace newFace,
			double newX, double newY, double newZ) {
		this.binId = newBinId;
		this.laneId = newLaneId;
		this.accessFace = newFace;
		this.x = newX;
		this.y = newY;
		this.z = newZ;
	}
}
