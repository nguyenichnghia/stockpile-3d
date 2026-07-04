-- V3: Multi-warehouse (ADR-0009) — independent warehouses in one database.
-- Blocking stays lane-local; the partition key becomes (warehouse_id, lane_id).
-- Existing data (a single implicit warehouse) is backfilled into a default
-- 'MAIN' warehouse, which is only created when there is data to own.

-- ---------------------------------------------------------------------------
-- warehouse: a physical site with its own local coordinate origin (dock at 0,0,0)
-- ---------------------------------------------------------------------------
CREATE TABLE warehouse (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(32)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT warehouse_code_uk UNIQUE (code)
);

-- Default warehouse for pre-existing single-warehouse data. movement can exist
-- without any location (INBOUND to staging has no bins), hence the three checks.
INSERT INTO warehouse (code, name)
SELECT 'MAIN', 'Main warehouse'
WHERE EXISTS (SELECT 1 FROM location)
   OR EXISTS (SELECT 1 FROM movement)
   OR EXISTS (SELECT 1 FROM pick_order);

-- ---------------------------------------------------------------------------
-- location: bin codes are now unique per warehouse, not globally
-- ---------------------------------------------------------------------------
ALTER TABLE location ADD COLUMN warehouse_id BIGINT;

UPDATE location SET warehouse_id = (SELECT id FROM warehouse WHERE code = 'MAIN');

ALTER TABLE location
    ALTER COLUMN warehouse_id SET NOT NULL,
    ADD CONSTRAINT location_warehouse_fk FOREIGN KEY (warehouse_id) REFERENCES warehouse (id);

ALTER TABLE location DROP CONSTRAINT location_code_uk;
ALTER TABLE location
    ADD CONSTRAINT location_code_uk UNIQUE (warehouse_id, zone, aisle, rack, level, bin);

-- Lane-local lookups now filter by warehouse first.
DROP INDEX location_lane_idx;
CREATE INDEX location_wh_lane_idx ON location (warehouse_id, lane_id);

-- ---------------------------------------------------------------------------
-- movement: the ledger records which warehouse each event happened in.
-- Derived from the bins when present; INBOUND to staging (no bins) must state it.
-- ---------------------------------------------------------------------------
ALTER TABLE movement ADD COLUMN warehouse_id BIGINT;

UPDATE movement m
SET warehouse_id = COALESCE(
    (SELECT l.warehouse_id FROM location l WHERE l.id = m.to_bin),
    (SELECT l.warehouse_id FROM location l WHERE l.id = m.from_bin),
    (SELECT w.id FROM warehouse w WHERE w.code = 'MAIN'));

ALTER TABLE movement
    ALTER COLUMN warehouse_id SET NOT NULL,
    ADD CONSTRAINT movement_warehouse_fk FOREIGN KEY (warehouse_id) REFERENCES warehouse (id);

-- Per-warehouse throughput scans (reporting).
CREATE INDEX movement_wh_ts_idx ON movement (warehouse_id, ts);

-- ---------------------------------------------------------------------------
-- pick_order: an order is fulfilled from exactly one warehouse
-- ---------------------------------------------------------------------------
ALTER TABLE pick_order ADD COLUMN warehouse_id BIGINT;

UPDATE pick_order SET warehouse_id = (SELECT id FROM warehouse WHERE code = 'MAIN');

ALTER TABLE pick_order
    ALTER COLUMN warehouse_id SET NOT NULL,
    ADD CONSTRAINT pick_order_warehouse_fk FOREIGN KEY (warehouse_id) REFERENCES warehouse (id);

CREATE INDEX pick_order_warehouse_idx ON pick_order (warehouse_id);
