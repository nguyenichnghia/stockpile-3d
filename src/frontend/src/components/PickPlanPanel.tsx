"use client";

import { useState } from "react";

import type { PickPlan, PickStep } from "@/lib/api";

/**
 * Side panel presenting a proposed pick-list: per-line fulfilment, the ordered
 * steps, and confirmation of the current step. Presentational only — the engine
 * proposed the plan, the user confirms each step, and the parent records the
 * movement (the 3D layer never moves lots on its own).
 *
 * Confirming by scanning the lot barcode ("LOT-{id}", ADR-0007) is the primary
 * path — the scan value is recorded as the movement's scanRef, giving the
 * ledger ground truth that the right physical box was touched. A manual
 * fallback stays available but records scanRef=null, so unscanned steps remain
 * auditable — unless the warehouse enforces scanning (requireScan), in which
 * case the backend would reject scanRef=null and the fallback is not offered.
 */
export default function PickPlanPanel({
  plan,
  stepIndex,
  busy,
  error,
  requireScan,
  binCodeOf,
  onConfirm,
  onClose,
}: {
  plan: PickPlan;
  /** Index of the next step to execute; steps before it are already done. */
  stepIndex: number;
  busy: boolean;
  error: string | null;
  /** Warehouse policy: when true, scanning is the only way to confirm a step. */
  requireScan: boolean;
  binCodeOf: (binId: number) => string;
  /** Confirm the current step; scanRef is the scanned code, or null if manual. */
  onConfirm: (scanRef: string | null) => void;
  onClose: () => void;
}) {
  const done = stepIndex >= plan.steps.length;
  const current = done ? null : plan.steps[stepIndex];

  return (
    <aside
      style={{
        position: "absolute",
        zIndex: 1,
        top: 56,
        right: 16,
        width: 300,
        maxHeight: "calc(100vh - 80px)",
        display: "flex",
        flexDirection: "column",
        gap: 10,
        padding: 12,
        borderRadius: 8,
        border: "1px solid #33406b",
        background: "rgba(15,22,48,0.92)",
        color: "#e6ecff",
        fontFamily: "system-ui, sans-serif",
        fontSize: 13,
      }}
    >
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <strong>Pick-list — đơn #{plan.orderId}</strong>
        <button
          type="button"
          onClick={onClose}
          aria-label="Đóng pick-list"
          style={{
            border: "none",
            background: "transparent",
            color: "#9fb0d8",
            fontSize: 15,
            cursor: "pointer",
          }}
        >
          ✕
        </button>
      </header>

      {/* Per-line fulfilment; a shortfall means the warehouse lacks stock. */}
      <ul style={{ margin: 0, padding: 0, listStyle: "none", color: "#9fb0d8" }}>
        {plan.lines.map((line) => (
          <li key={line.sku}>
            {line.sku}: {line.fulfilled}/{line.requested}
            {line.shortfall > 0 && (
              <span style={{ color: "#ff8a7a" }}> — thiếu {line.shortfall}</span>
            )}
          </li>
        ))}
      </ul>

      {plan.steps.length === 0 ? (
        <p style={{ margin: 0, color: "#9fb0d8" }}>
          Không có bước nào — không lô nào khớp đơn này còn trong kho.
        </p>
      ) : (
        <ol style={{ margin: 0, padding: 0, listStyle: "none", overflowY: "auto" }}>
          {plan.steps.map((step, i) => {
            const isDone = i < stepIndex;
            const isCurrent = i === stepIndex;
            return (
              <li
                key={i}
                style={{
                  padding: "5px 8px",
                  borderRadius: 5,
                  border: isCurrent ? "1px solid #ffb347" : "1px solid transparent",
                  color: isDone ? "#5b6a94" : isCurrent ? "#ffd9a0" : "#e6ecff",
                  textDecoration: isDone ? "line-through" : "none",
                }}
              >
                {i + 1}. {step.kind === "PICK" ? "Lấy" : "Dời"} lô #{step.lotId} —{" "}
                {binCodeOf(step.fromBinId)}
                {step.toBinId != null && ` → ${binCodeOf(step.toBinId)}`}
                {isDone && " ✓"}
              </li>
            );
          })}
        </ol>
      )}

      {error && <p style={{ margin: 0, color: "#ff8a7a" }}>{error}</p>}

      {done ? (
        plan.steps.length > 0 && (
          <p style={{ margin: 0, color: "#37e0a0" }}>✓ Hoàn tất pick-list</p>
        )
      ) : (
        // key remounts the form per step: scan value and error never leak
        // from one step into the next.
        <ScanConfirm
          key={stepIndex}
          step={current!}
          stepNumber={stepIndex + 1}
          stepTotal={plan.steps.length}
          busy={busy}
          requireScan={requireScan}
          onConfirm={onConfirm}
        />
      )}
    </aside>
  );
}

/**
 * Scan-to-confirm for one step: the operator scans the lot barcode; only the
 * expected code confirms. The manual fallback confirms with scanRef=null —
 * hidden when the warehouse enforces scanning, since the backend would reject
 * the movement anyway.
 */
function ScanConfirm({
  step,
  stepNumber,
  stepTotal,
  busy,
  requireScan,
  onConfirm,
}: {
  step: PickStep;
  stepNumber: number;
  stepTotal: number;
  busy: boolean;
  requireScan: boolean;
  onConfirm: (scanRef: string | null) => void;
}) {
  const [scan, setScan] = useState("");
  const [scanError, setScanError] = useState<string | null>(null);
  const expected = `LOT-${step.lotId}`;

  function submitScan(e: React.FormEvent) {
    e.preventDefault();
    const code = scan.trim();
    if (code.toUpperCase() === expected) {
      setScanError(null);
      onConfirm(code);
    } else {
      setScanError(`Sai mã — bước này cần ${expected}`);
    }
  }

  return (
    <>
      {scanError && <p style={{ margin: 0, color: "#ff8a7a" }}>{scanError}</p>}
      {/* Primary path: scan the lot barcode to confirm the step. */}
      <form onSubmit={submitScan} style={{ display: "flex", gap: 6 }}>
        <input
          value={scan}
          onChange={(e) => setScan(e.target.value)}
          placeholder={`Quét mã lô (${expected})…`}
          autoFocus
          style={{
            flex: 1,
            minWidth: 0,
            padding: "6px 10px",
            borderRadius: 6,
            border: "1px solid #33406b",
            background: "#0f1630",
            color: "#e6ecff",
            fontSize: 13,
          }}
        />
        <button
          type="submit"
          disabled={busy}
          style={{
            padding: "6px 12px",
            borderRadius: 6,
            border: "1px solid #33406b",
            background: "#5b6cff",
            color: "#fff",
            fontSize: 13,
            cursor: "pointer",
          }}
        >
          {busy ? "…" : `Quét bước ${stepNumber}/${stepTotal}`}
        </button>
      </form>
      {/* Fallback without a scanner: still confirms, but scanRef stays null.
          Not offered when the warehouse enforces scanning. */}
      {requireScan ? (
        <p style={{ margin: 0, color: "#9fb0d8", fontSize: 12 }}>
          Kho này bắt buộc quét mã để xác nhận.
        </p>
      ) : (
        <button
          type="button"
          onClick={() => onConfirm(null)}
          disabled={busy}
          style={{
            padding: "5px 12px",
            borderRadius: 6,
            border: "1px solid #33406b",
            background: "transparent",
            color: "#9fb0d8",
            fontSize: 12,
            cursor: "pointer",
          }}
        >
          Xác nhận không quét
        </button>
      )}
    </>
  );
}
