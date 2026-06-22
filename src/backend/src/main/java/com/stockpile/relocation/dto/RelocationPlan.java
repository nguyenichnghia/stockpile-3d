package com.stockpile.relocation.dto;

import java.util.List;

/**
 * The ordered moves needed to free a target lot. Empty steps means the lot is
 * already accessible. This is a proposal only — executing it is a separate,
 * user-confirmed action (the 3D layer never moves lots on its own).
 */
public record RelocationPlan(long targetLotId, List<RelocationStep> steps) {
}
