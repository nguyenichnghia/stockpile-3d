-- V5: Per-warehouse timezone for reporting. Aggregates ("today", daily
-- throughput buckets) follow the warehouse's local calendar instead of UTC.
-- The ledger itself is untouched — movement.ts stays TIMESTAMPTZ (an instant);
-- only how instants are bucketed into days changes. IANA zone id, e.g.
-- 'Asia/Ho_Chi_Minh'; default 'UTC' keeps existing warehouses unchanged.
ALTER TABLE warehouse ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';
