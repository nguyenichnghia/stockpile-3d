-- V1: Core schema for Stockpile-3D.
-- Five entities from docs/01 §6. PKs are BIGINT identity.
-- Enums stored as VARCHAR + CHECK (maps to JPA @Enumerated(STRING)).
--
-- Architectural invariants (see CLAUDE.md):
--   * movement is an append-only ledger and is the source of truth.
--   * placement is a projection rebuilt from the ledger (app-maintained).
--   * blocking is reasoned locally per lane; indexes support lane lookups.

-- ---------------------------------------------------------------------------
-- location: warehouse space frame (zone -> aisle -> rack -> level -> bin)
-- ---------------------------------------------------------------------------
CREATE TABLE location (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    zone        VARCHAR(64)  NOT NULL,
    aisle       VARCHAR(64)  NOT NULL,
    rack        VARCHAR(64)  NOT NULL,
    level       VARCHAR(64)  NOT NULL,
    bin         VARCHAR(64)  NOT NULL,
    -- corner coordinates of the slot
    x           NUMERIC(12, 3) NOT NULL,
    y           NUMERIC(12, 3) NOT NULL,
    z           NUMERIC(12, 3) NOT NULL,
    -- slot dimensions (width, depth, height)
    w           NUMERIC(12, 3) NOT NULL,
    d           NUMERIC(12, 3) NOT NULL,
    h           NUMERIC(12, 3) NOT NULL,
    lane_id     VARCHAR(64)  NOT NULL,
    -- retrieval side of the lane
    access_face VARCHAR(16)  NOT NULL
        CONSTRAINT location_access_face_chk
        CHECK (access_face IN ('NORTH', 'SOUTH', 'EAST', 'WEST', 'TOP')),
    CONSTRAINT location_code_uk UNIQUE (zone, aisle, rack, level, bin)
);

-- Lane-local lookups for the blocking graph.
CREATE INDEX location_lane_idx ON location (lane_id);
CREATE INDEX location_zar_idx  ON location (zone, aisle, rack);

-- ---------------------------------------------------------------------------
-- sku: product master
-- ---------------------------------------------------------------------------
CREATE TABLE sku (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code     VARCHAR(64)  NOT NULL,
    name     VARCHAR(255) NOT NULL,
    w        NUMERIC(12, 3) NOT NULL,
    d        NUMERIC(12, 3) NOT NULL,
    h        NUMERIC(12, 3) NOT NULL,
    weight   NUMERIC(12, 3) NOT NULL,
    handling VARCHAR(8)   NOT NULL
        CONSTRAINT sku_handling_chk CHECK (handling IN ('FIFO', 'FEFO')),
    CONSTRAINT sku_code_uk UNIQUE (code)
);

-- ---------------------------------------------------------------------------
-- lot: a physical unit placed in the warehouse
-- ---------------------------------------------------------------------------
CREATE TABLE lot (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku_id                 BIGINT NOT NULL
        CONSTRAINT lot_sku_fk REFERENCES sku (id),
    -- bounding box of this lot
    w                      NUMERIC(12, 3) NOT NULL,
    d                      NUMERIC(12, 3) NOT NULL,
    h                      NUMERIC(12, 3) NOT NULL,
    weight                 NUMERIC(12, 3) NOT NULL,
    expiry                 DATE,
    predicted_retrieval_at TIMESTAMPTZ
);

CREATE INDEX lot_sku_idx ON lot (sku_id);

-- ---------------------------------------------------------------------------
-- placement: which bin a lot currently occupies (projection from ledger)
-- ---------------------------------------------------------------------------
CREATE TABLE placement (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lot_id  BIGINT NOT NULL
        CONSTRAINT placement_lot_fk REFERENCES lot (id),
    bin_id  BIGINT NOT NULL
        CONSTRAINT placement_bin_fk REFERENCES location (id),
    -- pose of the lot within the bin
    x       NUMERIC(12, 3) NOT NULL,
    y       NUMERIC(12, 3) NOT NULL,
    z       NUMERIC(12, 3) NOT NULL,
    -- a lot occupies at most one location at a time
    CONSTRAINT placement_lot_uk UNIQUE (lot_id)
);

CREATE INDEX placement_bin_idx ON placement (bin_id);

-- ---------------------------------------------------------------------------
-- movement: append-only physical ledger (source of truth)
-- Rows are inserted, never updated or deleted (enforced at the app layer).
-- ---------------------------------------------------------------------------
CREATE TABLE movement (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lot_id   BIGINT NOT NULL
        CONSTRAINT movement_lot_fk REFERENCES lot (id),
    type     VARCHAR(16) NOT NULL
        CONSTRAINT movement_type_chk
        CHECK (type IN ('INBOUND', 'PUTAWAY', 'RELOCATE', 'PICK', 'OUTBOUND')),
    from_bin BIGINT
        CONSTRAINT movement_from_bin_fk REFERENCES location (id),
    to_bin   BIGINT
        CONSTRAINT movement_to_bin_fk REFERENCES location (id),
    ts       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor    VARCHAR(128),
    scan_ref VARCHAR(128)
);

-- Ledger replay / per-lot history in chronological order.
CREATE INDEX movement_lot_ts_idx ON movement (lot_id, ts);
