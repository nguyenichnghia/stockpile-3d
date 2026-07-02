"use client";

import { useEffect, useMemo, useState } from "react";

import Warehouse3D from "@/components/Warehouse3D";
import {
  fetchHeatmap,
  locateBin,
  locateBySku,
  type Location,
  type Placement,
} from "@/lib/api";
import { applyDelta, connectPlacements } from "@/lib/realtime";

/**
 * Client wrapper around the 3D scene. Adds SKU/bin locate, heatmap, and a live
 * STOMP subscription: placements are seeded from the server-rendered snapshot
 * and then updated in place as deltas arrive. The 3D layer only presents — both
 * search decisions and state changes originate in the backend.
 */
export default function WarehouseView({
  locations,
  placements: initialPlacements,
}: {
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

  // Subscribe to the lanes present in the loaded warehouse; apply each delta by
  // lotId (add / move / remove). Reconnect/cleanup handled by the disposer.
  const laneIds = useMemo(
    () => Array.from(new Set(locations.map((l) => l.laneId))),
    [locations],
  );
  useEffect(() => {
    if (laneIds.length === 0) return;
    return connectPlacements(laneIds, (d) => {
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
  }, [laneIds]);

  async function searchBin(e: React.SyntheticEvent) {
    e.preventDefault();
    const code = binQuery.trim();
    if (!code) {
      setLocatedBinId(null);
      setStatus(null);
      return;
    }
    setBusy(true);
    try {
      const result = await locateBin(code);
      setLocatedBinId(result.found ? result.binId : null);
      setStatus(
        result.found ? `Ô ${result.code}` : `Không tìm thấy ô "${code}"`,
      );
    } catch (err) {
      setLocatedBinId(null);
      setStatus(err instanceof Error ? err.message : "Lỗi tra ô");
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
      const result = await fetchHeatmap(m);
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
      const result = await locateBySku(sku);
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
        {!heatmap && (
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
              onKeyDown={(e) => e.key === "Enter" && searchBin(e)}
              placeholder="Tra mã ô (A-01-00-1-01)…"
              style={{
                padding: "6px 10px",
                borderRadius: 6,
                border: "1px solid #33406b",
                background: "#0f1630",
                color: "#e6ecff",
                fontSize: 13,
              }}
            />
            <button type="button" onClick={searchBin} disabled={busy} style={btnStyle}>
              Tra ô
            </button>
            {(highlighted || locatedBinId != null) && (
              <button type="button" onClick={clear} style={btnStyle}>
                Xóa
              </button>
            )}
          </>
        )}
        {heatmap ? (
          <button type="button" onClick={() => setHeatmap(undefined)} style={btnStyle}>
            Tắt heatmap
          </button>
        ) : (
          <select
            value=""
            disabled={busy}
            onChange={(e) => e.target.value && loadHeatmap(e.target.value as HeatmapMetric)}
            style={{
              padding: "6px 10px",
              borderRadius: 6,
              border: "1px solid #33406b",
              background: "#0f1630",
              color: "#e6ecff",
              fontSize: 13,
            }}
          >
            <option value="">{busy ? "…" : "Heatmap…"}</option>
            <option value="fill">Mức đầy</option>
            <option value="blocking">Độ bị chặn</option>
            <option value="expiry">Sắp hết hạn</option>
          </select>
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
        {status && !heatmap && (
          <span style={{ color: "#9fb0d8", fontSize: 13 }}>{status}</span>
        )}
      </form>

      <Warehouse3D
        locations={locations}
        placements={placements}
        highlightedBinIds={highlighted}
        highlightedLocationBinId={locatedBinId}
        heatmap={heatmap}
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

const btnStyle: React.CSSProperties = {
  padding: "6px 12px",
  borderRadius: 6,
  border: "1px solid #33406b",
  background: "#5b6cff",
  color: "#fff",
  fontSize: 13,
  cursor: "pointer",
};
