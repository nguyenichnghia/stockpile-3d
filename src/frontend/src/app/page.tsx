import Link from "next/link";

import WarehouseView from "@/components/WarehouseView";
import {
  fetchLocations,
  fetchPlacements,
  fetchWarehouses,
  type Location,
  type Placement,
  type Warehouse,
} from "@/lib/api";

export const dynamic = "force-dynamic";

/**
 * The working warehouse is selected via the `?wh={id}` search param (ADR-0009);
 * without it the first warehouse is shown. Every fetch below is scoped to it.
 */
export default async function Home({
  searchParams,
}: {
  searchParams: Promise<{ wh?: string }>;
}) {
  let warehouses: Warehouse[] = [];
  let locations: Location[] = [];
  let placements: Placement[] = [];
  let error: string | null = null;

  const { wh } = await searchParams;
  let selected: Warehouse | null = null;

  try {
    warehouses = await fetchWarehouses();
    selected = warehouses.find((w) => w.id === Number(wh)) ?? warehouses[0] ?? null;
    if (selected) {
      [locations, placements] = await Promise.all([
        fetchLocations(selected.id),
        fetchPlacements(selected.id),
      ]);
    }
  } catch (e) {
    error = e instanceof Error ? e.message : "Unknown error";
  }

  return (
    <main style={{ height: "100vh", width: "100vw", position: "relative" }}>
      <header
        style={{
          position: "absolute",
          zIndex: 1,
          top: 0,
          left: 0,
          padding: "12px 16px",
          color: "#e6ecff",
          fontFamily: "system-ui, sans-serif",
          pointerEvents: "none",
        }}
      >
        <strong>Stockpile-3D</strong>
        {warehouses.map((w) => (
          <Link
            key={w.id}
            href={`/?wh=${w.id}`}
            style={{
              pointerEvents: "auto",
              marginLeft: 12,
              fontSize: 13,
              color: w.id === selected?.id ? "#e6ecff" : "#9fb0d8",
              fontWeight: w.id === selected?.id ? 700 : 400,
              textDecoration: w.id === selected?.id ? "none" : "underline",
            }}
            title={w.name}
          >
            {w.code}
          </Link>
        ))}
        <Link
          href={selected ? `/reports?wh=${selected.id}` : "/reports"}
          style={{ pointerEvents: "auto", color: "#9fb0d8", fontSize: 13, marginLeft: 12 }}
        >
          Báo cáo →
        </Link>
        <div style={{ fontSize: 13, opacity: 0.8 }}>
          {error
            ? `Không tải được dữ liệu — ${error}`
            : selected
              ? `${selected.name} · ${locations.length} vị trí · ${placements.length} lô đang đặt`
              : "Chưa có kho nào"}
        </div>
      </header>

      {error || !selected ? (
        <div
          style={{
            display: "flex",
            height: "100%",
            alignItems: "center",
            justifyContent: "center",
            color: "#9fb0d8",
            fontFamily: "system-ui, sans-serif",
            background: "#0b1020",
            textAlign: "center",
            padding: 24,
          }}
        >
          <div>
            {error ? (
              <>
                <p>Backend chưa sẵn sàng.</p>
                <p style={{ fontSize: 13, opacity: 0.7 }}>
                  Chạy backend (cổng 8080) rồi tải lại trang.
                </p>
              </>
            ) : (
              <>
                <p>Chưa có kho nào.</p>
                <p style={{ fontSize: 13, opacity: 0.7 }}>
                  Tạo kho qua POST /api/warehouses rồi sinh lưới bằng POST
                  /api/warehouses/{"{id}"}/generate (docs/warehouse-setup.md).
                </p>
              </>
            )}
          </div>
        </div>
      ) : (
        <WarehouseView
          key={selected.id}
          warehouseId={selected.id}
          locations={locations}
          placements={placements}
        />
      )}
    </main>
  );
}
