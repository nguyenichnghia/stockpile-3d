"use client";

import { useState } from "react";

import Warehouse3D from "@/components/Warehouse3D";
import { locateBySku, type Location, type Placement } from "@/lib/api";

/**
 * Client wrapper around the 3D scene that adds SKU locate/search: type a SKU
 * code, the matching lots are highlighted and the rest dimmed. The 3D layer
 * only presents — the search decision comes from the backend endpoint.
 */
export default function WarehouseView({
  locations,
  placements,
}: {
  locations: Location[];
  placements: Placement[];
}) {
  const [query, setQuery] = useState("");
  const [highlighted, setHighlighted] = useState<Set<number> | undefined>();
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

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
        {highlighted && (
          <button type="button" onClick={clear} style={btnStyle}>
            Xóa
          </button>
        )}
        {status && (
          <span style={{ color: "#9fb0d8", fontSize: 13 }}>{status}</span>
        )}
      </form>

      <Warehouse3D
        locations={locations}
        placements={placements}
        highlightedBinIds={highlighted}
      />
    </>
  );
}

const btnStyle: React.CSSProperties = {
  padding: "6px 12px",
  borderRadius: 6,
  border: "1px solid #33406b",
  background: "#5b6cff",
  color: "#fff",
  fontSize: 13,
  cursor: "pointer",
};
