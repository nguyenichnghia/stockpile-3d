"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";

import {
  fetchMovementsDaily,
  fetchReportSummary,
  fetchWarehouses,
  simulateLayout,
  type MovementDaily,
  type ReportSummary,
  type Warehouse,
  type WhatIfResult,
} from "@/lib/api";

/**
 * Management dashboard: KPI tiles + ledger throughput per day, aggregated by
 * the backend (`/api/reports/*`). Read-only, same sources as the 3D scene.
 *
 * Chart conventions follow the dataviz method: series colors are assigned to
 * movement types in a fixed order (validated for CVD separation on this
 * surface), text stays in ink tokens, stacked segments keep a 2px surface gap,
 * a legend + hover tooltip + table view accompany the chart.
 */

// Movement types in flow order; each owns its categorical slot permanently.
const TYPES = ["INBOUND", "PUTAWAY", "RELOCATE", "PICK", "OUTBOUND"] as const;
const TYPE_LABEL: Record<string, string> = {
  INBOUND: "Nhập kho",
  PUTAWAY: "Xếp vào ô",
  RELOCATE: "Dời ô",
  PICK: "Lấy hàng",
  OUTBOUND: "Xuất kho",
};
// Categorical slots 1–5 (dark mode), validated vs surface #0f1630.
const TYPE_COLOR: Record<string, string> = {
  INBOUND: "#3987e5",
  PUTAWAY: "#199e70",
  RELOCATE: "#c98500",
  PICK: "#008300",
  OUTBOUND: "#9085e9",
};

const INK = "#e6ecff";
const INK_2 = "#9fb0d8";
const INK_MUTED = "#5b6a94";
const GRID = "#1e2a4a";
const SURFACE = "#0f1630";
// Status colors (reserved; always paired with an icon + label).
const WARNING = "#fab219";
const SERIOUS = "#ec835a";
const CRITICAL = "#d03b3b";

const DAYS = 14;

/** useSearchParams needs a Suspense boundary during prerender. */
export default function ReportsPageShell() {
  return (
    <Suspense fallback={null}>
      <ReportsPage />
    </Suspense>
  );
}

function ReportsPage() {
  const router = useRouter();
  const whParam = Number(useSearchParams().get("wh"));
  const [warehouses, setWarehouses] = useState<Warehouse[] | null>(null);
  // Report data tagged with its warehouse: stale data for another warehouse is
  // simply not shown, so switching needs no reset and late responses can't win.
  const [data, setData] = useState<{
    whId: number;
    summary: ReportSummary;
    rows: MovementDaily[];
  } | null>(null);
  const [error, setError] = useState<string | null>(null);

  // The working warehouse: the ?wh= param when valid, else the first one.
  const selected =
    warehouses?.find((w) => w.id === whParam) ?? warehouses?.[0] ?? null;
  const selectedId = selected?.id;

  useEffect(() => {
    fetchWarehouses()
      .then(setWarehouses)
      .catch((e) => setError(e instanceof Error ? e.message : "Lỗi tải danh sách kho"));
  }, []);

  useEffect(() => {
    if (selectedId == null) return;
    Promise.all([fetchReportSummary(selectedId), fetchMovementsDaily(DAYS, selectedId)])
      .then(([s, m]) => setData({ whId: selectedId, summary: s, rows: m }))
      .catch((e) => setError(e instanceof Error ? e.message : "Lỗi tải báo cáo"));
  }, [selectedId]);

  const summary = data && data.whId === selectedId ? data.summary : null;
  const rows = data && data.whId === selectedId ? data.rows : null;

  return (
    <main
      style={{
        minHeight: "100vh",
        background: "#0b1020",
        color: INK,
        fontFamily: "system-ui, sans-serif",
        padding: "24px 32px 48px",
      }}
    >
      <header style={{ display: "flex", alignItems: "baseline", gap: 16, marginBottom: 20 }}>
        <h1 style={{ margin: 0, fontSize: 20 }}>Báo cáo kho</h1>
        {warehouses && warehouses.length > 0 && (
          <select
            value={selected?.id ?? ""}
            onChange={(e) => router.push(`/reports?wh=${e.target.value}`)}
            style={{
              padding: "4px 8px",
              borderRadius: 6,
              border: "1px solid #33406b",
              background: SURFACE,
              color: INK,
              fontSize: 13,
            }}
          >
            {warehouses.map((w) => (
              <option key={w.id} value={w.id}>
                {w.code} — {w.name}
              </option>
            ))}
          </select>
        )}
        <Link href={selected ? `/?wh=${selected.id}` : "/"} style={{ color: INK_2, fontSize: 13 }}>
          ← Về kho 3D
        </Link>
      </header>

      {error && <p style={{ color: SERIOUS }}>{error}</p>}
      {!error && warehouses && warehouses.length === 0 && (
        <p style={{ color: INK_2 }}>Chưa có kho nào — tạo kho qua POST /api/warehouses.</p>
      )}
      {!error && selected && (!summary || !rows) && <p style={{ color: INK_2 }}>Đang tải…</p>}

      {summary && (
        <section
          aria-label="Chỉ số chính"
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))",
            gap: 12,
            marginBottom: 24,
          }}
        >
          <Tile
            label="Mức lấp đầy"
            value={`${(summary.fillRate * 100).toFixed(1)}%`}
            sub={`${summary.occupiedBins}/${summary.totalBins} ô có hàng`}
          />
          <Tile label="Lô đang trong kho" value={String(summary.activeLots)} sub="kiện / pallet" />
          <Tile
            label="Lô bị chặn"
            value={String(summary.blockedLots)}
            sub="phải dời lô khác mới lấy được"
            statusColor={summary.blockedLots > 0 ? SERIOUS : undefined}
            statusIcon="▲"
          />
          <Tile
            label="Sắp hết hạn (30 ngày)"
            value={String(summary.expiringSoon)}
            sub="ưu tiên xuất theo FEFO"
            statusColor={summary.expiringSoon > 0 ? WARNING : undefined}
            statusIcon="◆"
          />
          <Tile
            label="Đã quá hạn"
            value={String(summary.expired)}
            sub="cần xử lý"
            statusColor={summary.expired > 0 ? CRITICAL : undefined}
            statusIcon="●"
          />
          <Tile label="Đơn đang chờ" value={String(summary.openOrders)} sub="trạng thái OPEN" />
          <Tile
            label="Movement hôm nay"
            value={String(summary.movementsToday)}
            sub="bút toán ledger (UTC)"
          />
        </section>
      )}

      {rows && <ThroughputChart rows={rows} />}

      {summary && selected && <WhatIfSection warehouseId={selected.id} />}
    </main>
  );
}

/**
 * Layout what-if: re-put the current stock into a hypothetical grid (backend
 * simulation, nothing persisted) and compare the two layouts side by side.
 */
function WhatIfSection({ warehouseId }: { warehouseId: number }) {
  const [zones, setZones] = useState(1);
  const [aisles, setAisles] = useState(2);
  const [racks, setRacks] = useState(2);
  const [levels, setLevels] = useState(1);
  const [bins, setBins] = useState(12);
  const [result, setResult] = useState<WhatIfResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setResult(
        await simulateLayout(warehouseId, {
          zones,
          aislesPerZone: aisles,
          racksPerAisle: racks,
          levelsPerRack: levels,
          binsPerLevel: bins,
          binWidth: 1,
          binDepth: 1,
          binHeight: 1,
          aisleGap: 2,
          accessFace: "TOP",
        }),
      );
    } catch (err) {
      setResult(null);
      setError(err instanceof Error ? err.message : "Lỗi mô phỏng");
    } finally {
      setBusy(false);
    }
  }

  const fields: [string, number, (v: number) => void][] = [
    ["Zone", zones, setZones],
    ["Aisle/zone", aisles, setAisles],
    ["Rack/aisle", racks, setRacks],
    ["Tầng/rack", levels, setLevels],
    ["Ô/tầng", bins, setBins],
  ];

  return (
    <section
      aria-label="Mô phỏng what-if"
      style={{
        marginTop: 24,
        background: SURFACE,
        border: "1px solid #33406b",
        borderRadius: 8,
        padding: 16,
        maxWidth: 900,
      }}
    >
      <h2 style={{ margin: "0 0 4px", fontSize: 14 }}>What-if · thử một layout khác</h2>
      <p style={{ margin: "0 0 12px", color: INK_MUTED, fontSize: 12 }}>
        Xếp lại toàn bộ lô hiện có vào lưới giả định bằng engine SLAP (mô phỏng
        trong bộ nhớ — không ghi gì vào kho). Ô 1×1×1 m, lối đi 2 m, lấy từ trên.
      </p>

      <form onSubmit={run} style={{ display: "flex", gap: 10, alignItems: "end", flexWrap: "wrap" }}>
        {fields.map(([label, value, set]) => (
          <label key={label} style={{ display: "flex", flexDirection: "column", gap: 3, fontSize: 11, color: INK_2 }}>
            {label}
            <input
              type="number"
              min={1}
              value={value}
              onChange={(e) => set(Math.max(1, Number(e.target.value)))}
              style={{
                width: 76,
                padding: "6px 8px",
                borderRadius: 6,
                border: "1px solid #33406b",
                background: "#0b1020",
                color: INK,
                fontSize: 13,
              }}
            />
          </label>
        ))}
        <button
          type="submit"
          disabled={busy}
          style={{
            padding: "7px 14px",
            borderRadius: 6,
            border: "1px solid #33406b",
            background: "#5b6cff",
            color: "#fff",
            fontSize: 13,
            cursor: "pointer",
          }}
        >
          {busy ? "…" : "Mô phỏng"}
        </button>
      </form>

      {error && <p style={{ color: SERIOUS, fontSize: 13 }}>{error}</p>}

      {result && (
        <table
          style={{
            marginTop: 14,
            borderCollapse: "collapse",
            fontSize: 13,
            color: INK,
            fontVariantNumeric: "tabular-nums",
          }}
        >
          <thead>
            <tr>
              <th style={{ ...thStyle, textAlign: "left" }}>Chỉ số</th>
              <th style={thStyle}>Hiện tại</th>
              <th style={thStyle}>Mô phỏng</th>
            </tr>
          </thead>
          <tbody>
            <CompareRow label="Số ô" a={result.current.bins} b={result.simulated.bins} />
            <CompareRow label="Lô xếp được" a={result.current.placedLots} b={result.simulated.placedLots} />
            <CompareRow
              label="Lô không còn chỗ"
              a={result.current.unplacedLots}
              b={result.simulated.unplacedLots}
              lowerIsBetter
            />
            <CompareRow
              label="Lô bị chặn"
              a={result.current.blockedLots}
              b={result.simulated.blockedLots}
              lowerIsBetter
            />
            <CompareRow
              label="Mức lấp đầy"
              a={result.current.fillRate}
              b={result.simulated.fillRate}
              fmt={(v) => `${(v * 100).toFixed(1)}%`}
            />
            <CompareRow
              label="Khoảng cách tới dock (TB)"
              a={result.current.avgDistToDock}
              b={result.simulated.avgDistToDock}
              fmt={(v) => v.toFixed(2) + " m"}
              lowerIsBetter
            />
          </tbody>
        </table>
      )}
    </section>
  );
}

/** One metric row; the delta is colored good/bad only when direction is known. */
function CompareRow({
  label,
  a,
  b,
  fmt = (v: number) => String(v),
  lowerIsBetter,
}: {
  label: string;
  a: number;
  b: number;
  fmt?: (v: number) => string;
  lowerIsBetter?: boolean;
}) {
  let deltaColor = INK_MUTED;
  let arrow = "";
  if (lowerIsBetter && b !== a) {
    const better = b < a;
    deltaColor = better ? "#0ca30c" : CRITICAL;
    arrow = better ? " ↓" : " ↑";
  }
  return (
    <tr>
      <td style={{ ...tdStyle, color: INK_2 }}>{label}</td>
      <td style={{ ...tdStyle, textAlign: "right" }}>{fmt(a)}</td>
      <td style={{ ...tdStyle, textAlign: "right", color: deltaColor === INK_MUTED ? INK : deltaColor }}>
        {fmt(b)}
        {arrow}
      </td>
    </tr>
  );
}

/** One KPI. Value/label stay in ink; a status is an icon + color beside them. */
function Tile({
  label,
  value,
  sub,
  statusColor,
  statusIcon,
}: {
  label: string;
  value: string;
  sub: string;
  statusColor?: string;
  statusIcon?: string;
}) {
  return (
    <div
      style={{
        background: SURFACE,
        border: "1px solid #33406b",
        borderRadius: 8,
        padding: "12px 14px",
      }}
    >
      <div style={{ color: INK_2, fontSize: 12 }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: 600, margin: "2px 0" }}>
        {statusColor && (
          <span aria-hidden style={{ color: statusColor, fontSize: 14, marginRight: 6 }}>
            {statusIcon}
          </span>
        )}
        {value}
      </div>
      <div style={{ color: INK_MUTED, fontSize: 11 }}>{sub}</div>
    </div>
  );
}

type DayStack = { date: string; counts: Record<string, number>; total: number };

/** Stacked bars: ledger entries per UTC day, one segment per movement type. */
function ThroughputChart({ rows }: { rows: MovementDaily[] }) {
  const [hover, setHover] = useState<number | null>(null);

  // Fill the whole window so quiet days show as gaps, not missing columns.
  const days = useMemo<DayStack[]>(() => {
    const byDate = new Map<string, Record<string, number>>();
    for (const r of rows) {
      const day = byDate.get(r.date) ?? {};
      day[r.type] = r.count;
      byDate.set(r.date, day);
    }
    const out: DayStack[] = [];
    const today = new Date();
    for (let i = DAYS - 1; i >= 0; i--) {
      const d = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate() - i));
      const key = d.toISOString().slice(0, 10);
      const counts = byDate.get(key) ?? {};
      out.push({
        date: key,
        counts,
        total: TYPES.reduce((s, t) => s + (counts[t] ?? 0), 0),
      });
    }
    return out;
  }, [rows]);

  const W = 720;
  const H = 260;
  const PAD = { top: 12, right: 12, bottom: 26, left: 36 };
  const plotW = W - PAD.left - PAD.right;
  const plotH = H - PAD.top - PAD.bottom;
  const max = Math.max(1, ...days.map((d) => d.total));
  const slot = plotW / DAYS;
  const barW = Math.min(28, slot * 0.6);
  const y = (v: number) => PAD.top + plotH - (v / max) * plotH;
  const ticks = niceTicks(max);

  const fmt = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;

  return (
    <section
      aria-label="Movement theo ngày"
      style={{
        background: SURFACE,
        border: "1px solid #33406b",
        borderRadius: 8,
        padding: 16,
        maxWidth: 900,
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", flexWrap: "wrap", gap: 8 }}>
        <h2 style={{ margin: 0, fontSize: 14 }}>Movement theo ngày · {DAYS} ngày gần nhất</h2>
        {/* Legend: chip carries the color, text stays ink. */}
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
          {TYPES.map((t) => (
            <span key={t} style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 12, color: INK_2 }}>
              <span
                aria-hidden
                style={{ width: 10, height: 10, borderRadius: 2, background: TYPE_COLOR[t] }}
              />
              {TYPE_LABEL[t]}
            </span>
          ))}
        </div>
      </div>

      <div style={{ position: "relative" }}>
        <svg viewBox={`0 0 ${W} ${H}`} style={{ width: "100%", height: "auto", display: "block" }}>
          {/* Recessive grid + y ticks (integers). */}
          {ticks.map((t) => (
            <g key={t}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} stroke={GRID} strokeWidth={1} />
              <text x={PAD.left - 6} y={y(t) + 3} textAnchor="end" fontSize={10} fill={INK_MUTED}>
                {t}
              </text>
            </g>
          ))}
          {/* Baseline. */}
          <line
            x1={PAD.left}
            x2={W - PAD.right}
            y1={PAD.top + plotH}
            y2={PAD.top + plotH}
            stroke="#33406b"
            strokeWidth={1}
          />

          {days.map((d, i) => {
            const cx = PAD.left + slot * i + slot / 2;
            let acc = 0;
            const segs = TYPES.flatMap((t) => {
              const v = d.counts[t] ?? 0;
              if (v === 0) return [];
              const y1 = y(acc + v);
              const h = y(acc) - y(acc + v);
              acc += v;
              return [{ t, y1, h, isTop: acc === d.total }];
            });
            return (
              <g key={d.date}>
                {segs.map((s) => (
                  <rect
                    key={s.t}
                    x={cx - barW / 2}
                    y={s.y1}
                    width={barW}
                    // 2px surface gap between stacked segments; the data-end
                    // (top segment) is the only rounded edge.
                    height={Math.max(0, s.h - 2)}
                    rx={s.isTop ? 4 : 0}
                    fill={TYPE_COLOR[s.t]}
                  />
                ))}
                {/* Hover target: the whole day column, larger than the marks. */}
                <rect
                  x={PAD.left + slot * i}
                  y={PAD.top}
                  width={slot}
                  height={plotH}
                  fill="transparent"
                  onMouseEnter={() => setHover(i)}
                  onMouseLeave={() => setHover(null)}
                />
                {/* Every other x label to avoid collisions. */}
                {i % 2 === 0 && (
                  <text x={cx} y={H - 8} textAnchor="middle" fontSize={10} fill={INK_MUTED}>
                    {fmt(d.date)}
                  </text>
                )}
              </g>
            );
          })}
        </svg>

        {hover != null && days[hover] && (
          <div
            style={{
              position: "absolute",
              left: `${((PAD.left + slot * hover + slot / 2) / W) * 100}%`,
              top: 0,
              transform: `translateX(${hover > DAYS / 2 ? "-105%" : "5%"})`,
              background: "rgba(11,16,32,0.95)",
              border: "1px solid #33406b",
              borderRadius: 6,
              padding: "8px 10px",
              fontSize: 12,
              pointerEvents: "none",
              whiteSpace: "nowrap",
            }}
          >
            <div style={{ color: INK, marginBottom: 4 }}>{fmt(days[hover].date)}</div>
            {TYPES.map((t) => (
              <div key={t} style={{ display: "flex", alignItems: "center", gap: 5, color: INK_2 }}>
                <span aria-hidden style={{ width: 8, height: 8, borderRadius: 2, background: TYPE_COLOR[t] }} />
                {TYPE_LABEL[t]}: {days[hover].counts[t] ?? 0}
              </div>
            ))}
            <div style={{ color: INK, marginTop: 4 }}>Tổng: {days[hover].total}</div>
          </div>
        )}
      </div>

      {/* Table view of the same data (accessibility / print). */}
      <details style={{ marginTop: 10 }}>
        <summary style={{ color: INK_2, fontSize: 12, cursor: "pointer" }}>Bảng số liệu</summary>
        <table
          style={{
            marginTop: 8,
            borderCollapse: "collapse",
            fontSize: 12,
            color: INK,
            fontVariantNumeric: "tabular-nums",
          }}
        >
          <thead>
            <tr>
              <th style={thStyle}>Ngày</th>
              {TYPES.map((t) => (
                <th key={t} style={thStyle}>
                  {TYPE_LABEL[t]}
                </th>
              ))}
              <th style={thStyle}>Tổng</th>
            </tr>
          </thead>
          <tbody>
            {days.map((d) => (
              <tr key={d.date}>
                <td style={tdStyle}>{fmt(d.date)}</td>
                {TYPES.map((t) => (
                  <td key={t} style={{ ...tdStyle, textAlign: "right" }}>
                    {d.counts[t] ?? 0}
                  </td>
                ))}
                <td style={{ ...tdStyle, textAlign: "right" }}>{d.total}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </section>
  );
}

/** Integer ticks: 0, half, max (deduplicated for tiny maxima). */
function niceTicks(max: number): number[] {
  return Array.from(new Set([0, Math.round(max / 2), max]));
}

const thStyle: React.CSSProperties = {
  textAlign: "right",
  padding: "3px 10px",
  borderBottom: "1px solid #33406b",
  color: "#9fb0d8",
  fontWeight: 500,
};

const tdStyle: React.CSSProperties = {
  padding: "3px 10px",
  borderBottom: "1px solid #1e2a4a",
};
