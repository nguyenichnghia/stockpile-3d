package com.stockpile.picking.dto;

import java.util.List;

/**
 * A proposed pick-list for an order: per-line fulfilment plus the ordered
 * sequence of steps (relocations interleaved with picks). Proposal only — the
 * 3D layer presents it and the user confirms; executing it records movements.
 */
public record PickPlan(long orderId, List<PickLineResult> lines, List<PickStep> steps) {
}
