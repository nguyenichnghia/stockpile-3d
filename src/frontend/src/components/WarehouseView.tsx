"use client";

import { useEffect, useMemo, useState } from "react";

import PickPlanPanel from "@/components/PickPlanPanel";
import Warehouse3D from "@/components/Warehouse3D";
import {
  binCode,
  fetchHeatmap,
  fetchOrders,
  fetchPickPlan,
  locateBySku,
  recordMovement,
  resolveScan,
  type Location,
  type Order,
  type PickPlan,
  type Placement,
} from "@/lib/api";
import { applyDelta, connectPlacements } from "@/lib/realtime";

/**
 * Client wrapper around the 3D scene. Adds SKU/bin locate, heatmap, pick-list
 * step-through, and a live STOMP subscription: placements are seeded from the
 * server-rendered snapshot and then updated in place as deltas arrive. The 3D
 * layer only presents — search decisions and state changes originate in the
 * backend; the one write here (confirming a pick-list step) records an
 * engine-proposed movement after explicit user confirmation.
 */
export default function WarehouseView({
  warehouseId,
  locations,
  placements: initialPlacements,
}: {
  warehouseId: number;
  locations: Location[];
  placements: Placement[];
}) {
  const [placements, setPlacements] = useState<Placement[]>(initialPlacements);
  const [query, setQuery] = useState("");
  const [highlighted, setHighlighted] = useState<Set<number> | undefined>();
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [heatmap, setHeatmap] = useState<Map<number, number> | undefined>();
  const [metric, setMetric] = useState<HeatmapMetric>("fill");
  const [binQuery, setBinQuery] = useState("");
  const [locatedBinId, setLocatedBinId] = useState<number | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [plan, setPlan] = useState<PickPlan | null>(null);
  const [stepIndex, setStepIndex] = useState(0);
  const [planBusy, setPlanBusy] = useState(false);
  const [planError, setPlanError] = useState<string | null>(null);

  // Subscribe to the lanes present in the loaded warehouse; apply each delta by
  // lotId (add / move / remove). Reconnect/cleanup handled by the disposer.
  const laneIds = useMemo(
    () => Array.from(new Set(locations.map((l) => l.laneId))),
    [locations],
  );
  useEffect(() => {
    if (laneIds.length === 0) return;
    return connectPlacements(warehouseId, laneIds, (d) => {
      setPlacements((prev) =>
        applyDelta(prev, d, (delta) => ({
          id: -1, // delta carries no placement row id; consumers key off lotId/binId
          lotId: delta.lotId,
          binId: delta.binId!,
          x: delta.x!,
          y: delta.y!,
          z: delta.z!,
        })),
      );
    });
  }, [warehouseId, laneIds]);

  // Orders for the pick-list dropdown — only this warehouse's orders, since a
  // plan for another warehouse could not be shown on this scene. Loaded once; a
  // failure just leaves the list empty (the rest of the view works without it).
  useEffect(() => {
    fetchOrders()
      .then((all) => setOrders(all.filter((o) => o.warehouseId === warehouseId)))
      .catch(() => {});
  }, [warehouseId]);

  const binById = useMemo(
    () => new Map(locations.map((l) => [l.id, l])),
    [locations],
  );
  const binCodeOf = (binId: number) => {
    const bin = binById.get(binId);
    return bin ? binCode(bin) : `ô #${binId}`;
  };

  // The next step to execute, or null when the plan is finished.
  const currentStep =
    plan && stepIndex < plan.steps.length ? plan.steps[stepIndex] : null;

  async function openPlan(orderId: number) {
    clear(); // pick-list is its own mode; leave search first
    setHeatmap(undefined);
    setPlanBusy(true);
    setPlanError(null);
    try {
      const p = await fetchPickPlan(orderId);
      setPlan(p);
      setStepIndex(0);
    } catch (err) {
      setPlanError(err instanceof Error ? err.message : "Lỗi lập pick-list");
    } finally {
      setPlanBusy(false);
    }
  }

  // Records the current step in the ledger. The scene is NOT patched here: the
  // backend applies the projection and pushes a STOMP delta, and the placement
  // update flows in through the same realtime path as any other movement.
  // scanRef is the barcode the user scanned to confirm (null = manual confirm);
  // the ledger keeps it, so unscanned touches stay auditable (ADR-0007).
  async function confirmStep(scanRef: string | null) {
    if (!plan || !currentStep) return;
    setPlanBusy(true);
    setPlanError(null);
    try {
      await recordMovement({
        lotId: currentStep.lotId,
        type: currentStep.kind,
        fromBin: currentStep.fromBinId,
        toBin: currentStep.toBinId,
        scanRef,
      });
      setStepIndex((i) => i + 1);
    } catch (err) {
      setPlanError(err instanceof Error ? err.message : "Lỗi ghi bước");
    } finally {
      setPlanBusy(false);
    }
  }

  function closePlan() {
    setPlan(null);
    setStepIndex(0);
    setPlanError(null);
  }

  // One box for every scanned/typed code (ADR-0007): a bin code highlights the
  // bin frame; a "LOT-{id}" code highlights the bin its lot sits in (dimming
  // the rest, same as a SKU search with one match).
  async function searchScan(e: React.SyntheticEvent) {
    e.preventDefault();
    const code = binQuery.trim();
    setLocatedBinId(null);
    setHighlighted(undefined);
    if (!code) {
      setStatus(null);
      return;
    }
    setBusy(true);
    try {
      const result = await resolveScan(code, warehouseId);
      if (result.type === "BIN" && result.found && result.bin) {
        setLocatedBinId(result.bin.id);
        setStatus(
          `Ô ${result.bin.code}` +
            (result.bin.lotIds.length > 0 ? ` · ${result.bin.lotIds.length} lô` : " · trống"),
        );
      } else if (result.type === "LOT" && result.found && result.lot) {
        if (result.lot.binId != null) {
          setHighlighted(new Set([result.lot.binId]));
          setStatus(`Lô #${result.lot.id} (${result.lot.sku}) ở ô ${result.lot.binCode}`);
        } else {
          setStatus(`Lô #${result.lot.id} (${result.lot.sku}) chưa đặt trong kho`);
        }
      } else if (result.type === "LOT") {
        setStatus(`Không có lô "${code}"`);
      } else if (result.type === "BIN") {
        setStatus(`Không tìm thấy ô "${code}"`);
      } else {
        setStatus(`Mã không nhận dạng được: "${code}"`);
      }
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Lỗi tra mã");
    } finally {
      setBusy(false);
    }
  }

  async function loadHeatmap(m: HeatmapMetric) {
    // Heatmap and SKU search are separate modes; clear the search first.
    clear();
    setMetric(m);
    setBusy(true);
    try {
      const result = await fetchHeatmap(m, warehouseId);
      setHeatmap(new Map(result.cells.map((c) => [c.binId, c.value])));
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Lỗi tải heatmap");
    } finally {
      setBusy(false);
    }
  }

  async function search(e: React.FormEvent) {
    e.preventDefault();
    const sku = query.trim();
    if (!sku) {
      setHighlighted(undefined);
      setStatus(null);
      return;
    }
    setBusy(true);
    try {
      const result = await locateBySku(sku, warehouseId);
      setHighlighted(new Set(result.matches.map((m) => m.binId)));
      setStatus(
        result.matchCount > 0
          ? `Tìm thấy ${result.matchCount} lô của "${result.sku}"`
          : `Không có lô nào của "${result.sku}"`,
      );
    } catch (err) {
      setHighlighted(undefined);
      setStatus(err instanceof Error ? err.message : "Lỗi tra cứu");
    } finally {
      setBusy(false);
    }
  }

  function clear() {
    setQuery("");
    setHighlighted(undefined);
    setBinQuery("");
    setLocatedBinId(null);
    setStatus(null);
  }

  return (
    <>
      <form
        onSubmit={search}
        style={{
          position: "absolute",
          zIndex: 1,
          top: 56,
          left: 16,
          display: "flex",
          gap: 8,
          alignItems: "center",
          fontFamily: "system-ui, sans-serif",
        }}
      >
        {!heatmap && !plan && (
          <>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Tra mã hàng (SKU)…"
              style={{
                padding: "6px 10px",
                borderRadius: 6,
                border: "1px solid #33406b",
                background: "#0f1630",
                color: "#e6ecff",
                fontSize: 13,
              }}
            />
            <button type="submit" disabled={busy} style={btnStyle}>
              {busy ? "…" : "Tìm"}
            </button>
            <input
              value={binQuery}
              onChange={(e) => setBinQuery(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && searchScan(e)}
              placeholder="Quét mã (ô hoặc LOT-…)…"
              style={{
                padding: "6px 10px",
                borderRadius: 6,
                border: "1px solid #33406b",
                background: "#0f1630",
                color: "#e6ecff",
                fontSize: 13,
              }}
            />
            <button type="button" onClick={searchScan} disabled={busy} style={btnStyle}>
              Tra mã
            </button>
            {(highlighted || locatedBinId != null) && (
              <button type="button" onClick={clear} style={btnStyle}>
                Xóa
              </button>
            )}
          </>
        )}
        {heatmap && (
          <button type="button" onClick={() => setHeatmap(undefined)} style={btnStyle}>
            Tắt heatmap
          </button>
        )}
        {!heatmap && !plan && (
          <>
            <select
              value=""
              disabled={busy}
              onChange={(e) => e.target.value && loadHeatmap(e.target.value as HeatmapMetric)}
              style={selectStyle}
            >
              <option value="">{busy ? "…" : "Heatmap…"}</option>
              <option value="fill">Mức đầy</option>
              <option value="blocking">Độ bị chặn</option>
              <option value="expiry">Sắp hết hạn</option>
            </select>
            <select
              value=""
              disabled={planBusy}
              onChange={(e) => e.target.value && openPlan(Number(e.target.value))}
              style={selectStyle}
            >
              <option value="">{planBusy ? "…" : "Pick-list…"}</option>
              {orders.map((o) => (
                <option key={o.id} value={o.id}>
                  #{o.id} {o.code} ({o.status})
                </option>
              ))}
            </select>
          </>
        )}
        {heatmap && (
          <span
            style={{
              display: "flex",
              alignItems: "center",
              gap: 6,
              color: "#9fb0d8",
              fontSize: 12,
            }}
          >
            <span style={{ color: "#3fae4a" }}>■</span> {LEGEND[metric].low}
            <span
              style={{
                width: 60,
                height: 8,
                borderRadius: 4,
                background: "linear-gradient(90deg,#3fae4a,#d8d13a,#d84a3a)",
              }}
            />
            <span style={{ color: "#d84a3a" }}>■</span> {LEGEND[metric].high}
          </span>
        )}
        {status && !heatmap && !plan && (
          <span style={{ color: "#9fb0d8", fontSize: 13 }}>{status}</span>
        )}
        {planError && !plan && (
          <span style={{ color: "#ff8a7a", fontSize: 13 }}>{planError}</span>
        )}
      </form>

      {plan && (
        <PickPlanPanel
          plan={plan}
          stepIndex={stepIndex}
          busy={planBusy}
          error={planError}
          binCodeOf={binCodeOf}
          onConfirm={confirmStep}
          onClose={closePlan}
        />
      )}

      <Warehouse3D
        locations={locations}
        placements={placements}
        highlightedBinIds={
          plan
            ? currentStep
              ? new Set(
                  currentStep.toBinId != null
                    ? [currentStep.fromBinId, currentStep.toBinId]
                    : [currentStep.fromBinId],
                )
              : undefined
            : highlighted
        }
        highlightedLocationBinId={plan ? null : locatedBinId}
        heatmap={heatmap}
        planStep={currentStep}
      />
    </>
  );
}

type HeatmapMetric = "fill" | "blocking" | "expiry";

/** Legend labels for each metric's cool (green) and hot (red) ends. */
const LEGEND: Record<HeatmapMetric, { low: string; high: string }> = {
  fill: { low: "trống", high: "có hàng" },
  blocking: { low: "dễ lấy", high: "bị chặn" },
  expiry: { low: "còn hạn", high: "sắp hết hạn" },
};

const selectStyle: React.CSSProperties = {
  padding: "6px 10px",
  borderRadius: 6,
  border: "1px solid #33406b",
  background: "#0f1630",
  color: "#e6ecff",
  fontSize: 13,
};

const btnStyle: React.CSSProperties = {
  padding: "6px 12px",
  borderRadius: 6,
  border: "1px solid #33406b",
  background: "#5b6cff",
  color: "#fff",
  fontSize: 13,
  cursor: "pointer",
};
