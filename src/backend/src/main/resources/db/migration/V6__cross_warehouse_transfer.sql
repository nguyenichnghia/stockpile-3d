-- V6: Cross-warehouse transfer (ADR-0010, Phương án B).
-- A transfer moves a lot from warehouse A to warehouse B as a linked pair of
-- ledger entries: an OUTBOUND in A (lot leaves -> in-transit, no placement) and,
-- when the goods arrive, an INBOUND into a bin of B. This table only *links* the
-- two movements and carries the trip state; the lot's position is still derived
-- entirely from the movement ledger (the source of truth is unchanged).
--
-- No change to `movement`: it stays append-only with no extra column. Each of
-- the two movements is a normal single-warehouse entry, so the "reject
-- cross-warehouse movement" rule (ADR-0009) is untouched.
CREATE TABLE transfer (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lot_id               BIGINT NOT NULL
        CONSTRAINT transfer_lot_fk REFERENCES lot (id),
    from_warehouse_id    BIGINT NOT NULL
        CONSTRAINT transfer_from_wh_fk REFERENCES warehouse (id),
    to_warehouse_id      BIGINT NOT NULL
        CONSTRAINT transfer_to_wh_fk REFERENCES warehouse (id),
    status               VARCHAR(16) NOT NULL DEFAULT 'IN_TRANSIT'
        CONSTRAINT transfer_status_chk CHECK (status IN ('IN_TRANSIT', 'COMPLETED')),
    -- The OUTBOUND (in A) exists from the moment the transfer opens; the INBOUND
    -- (in B) is filled in on receipt, hence nullable.
    outbound_movement_id BIGINT NOT NULL
        CONSTRAINT transfer_outbound_fk REFERENCES movement (id),
    inbound_movement_id  BIGINT
        CONSTRAINT transfer_inbound_fk REFERENCES movement (id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ,
    -- A transfer must actually cross warehouses.
    CONSTRAINT transfer_distinct_wh_chk CHECK (from_warehouse_id <> to_warehouse_id)
);

-- List in-transit shipments per destination warehouse (the "đang chuyển" view).
CREATE INDEX transfer_to_wh_status_idx ON transfer (to_warehouse_id, status);
CREATE INDEX transfer_from_wh_idx      ON transfer (from_warehouse_id);
