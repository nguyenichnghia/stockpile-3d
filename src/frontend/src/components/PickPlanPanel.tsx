"use client";

import type { PickPlan } from "@/lib/api";

/**
 * Side panel presenting a proposed pick-list: per-line fulfilment, the ordered
 * steps, and a confirm button for the current step. Presentational only — the
 * engine proposed the plan, the user confirms each step, and the parent records
 * the movement (the 3D layer never moves lots on its own).
 */
export default function PickPlanPanel({
  plan,
  stepIndex,
  busy,
  error,
  binCodeOf,
  onConfirm,
  onClose,
}: {
  plan: PickPlan;
  /** Index of the next step to execute; steps before it are already done. */
  stepIndex: number;
  busy: boolean;
  error: string | null;
  binCodeOf: (binId: number) => string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const done = stepIndex >= plan.steps.length;

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
        <button
          type="button"
          onClick={onConfirm}
          disabled={busy}
          style={{
            padding: "8px 12px",
            borderRadius: 6,
            border: "1px solid #33406b",
            background: "#5b6cff",
            color: "#fff",
            fontSize: 13,
            cursor: "pointer",
          }}
        >
          {busy ? "…" : `Xác nhận bước ${stepIndex + 1}/${plan.steps.length}`}
        </button>
      )}
    </aside>
  );
}
