package com.stockpile.relocation.dto;

/** One move in a relocation plan: take {@code lotId} from {@code fromBinId} to {@code toBinId}. */
public record RelocationStep(long lotId, long fromBinId, long toBinId) {
}
