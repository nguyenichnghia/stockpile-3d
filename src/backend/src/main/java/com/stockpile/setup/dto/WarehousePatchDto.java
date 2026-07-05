package com.stockpile.setup.dto;

/**
 * Partial update for a warehouse's operating policy. Null fields are left
 * unchanged, so a client can flip one switch without restating the rest.
 * Identity (code) is immutable — codes are stable identifiers like SKU codes.
 */
public record WarehousePatchDto(Boolean requireScan, String timezone) {
}
