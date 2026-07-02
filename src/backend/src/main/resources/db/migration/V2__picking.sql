-- V2: Picking — customer orders and their lines (docs/01 §8.4).
-- The picking engine reads these to plan a pick-list; it does not write here.
-- Follows V1 conventions: BIGINT identity PKs, enums as VARCHAR + CHECK.
--
-- "order" is a SQL reserved word, so the table is named pick_order.

-- ---------------------------------------------------------------------------
-- pick_order: a request to retrieve stock (header)
-- ---------------------------------------------------------------------------
CREATE TABLE pick_order (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(64)  NOT NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'OPEN'
        CONSTRAINT pick_order_status_chk
        CHECK (status IN ('OPEN', 'PLANNED', 'PICKED', 'CANCELLED')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pick_order_code_uk UNIQUE (code)
);

-- ---------------------------------------------------------------------------
-- order_line: one SKU + quantity within an order
-- ---------------------------------------------------------------------------
CREATE TABLE order_line (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL
        CONSTRAINT order_line_order_fk REFERENCES pick_order (id) ON DELETE CASCADE,
    sku_id   BIGINT NOT NULL
        CONSTRAINT order_line_sku_fk REFERENCES sku (id),
    -- number of crates/boxes/pallets requested (the unit tracked, not a count of items)
    qty      INTEGER NOT NULL
        CONSTRAINT order_line_qty_chk CHECK (qty > 0)
);

CREATE INDEX order_line_order_idx ON order_line (order_id);
CREATE INDEX order_line_sku_idx   ON order_line (sku_id);
