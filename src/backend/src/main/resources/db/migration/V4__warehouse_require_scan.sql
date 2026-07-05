-- V4: Per-warehouse scan enforcement (the slice ADR-0007 left room for).
-- When require_scan is on, POST /api/movements rejects a movement whose
-- scanRef is missing or does not match the movement's lot barcode (LOT-{id}).
-- Default FALSE: existing warehouses keep the v1 encourage-and-audit contract.
ALTER TABLE warehouse ADD COLUMN require_scan BOOLEAN NOT NULL DEFAULT FALSE;
