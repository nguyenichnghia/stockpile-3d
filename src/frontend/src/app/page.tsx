import Warehouse3D from "@/components/Warehouse3D";
import { fetchLocations, fetchPlacements, type Location, type Placement } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function Home() {
  let locations: Location[] = [];
  let placements: Placement[] = [];
  let error: string | null = null;

  try {
    [locations, placements] = await Promise.all([fetchLocations(), fetchPlacements()]);
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
        <div style={{ fontSize: 13, opacity: 0.8 }}>
          {error
            ? `Không tải được dữ liệu — ${error}`
            : `${locations.length} vị trí · ${placements.length} lô đang đặt`}
        </div>
      </header>

      {error ? (
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
            <p>Backend chưa sẵn sàng.</p>
            <p style={{ fontSize: 13, opacity: 0.7 }}>
              Chạy backend (cổng 8080) rồi tải lại trang.
            </p>
          </div>
        </div>
      ) : (
        <Warehouse3D locations={locations} placements={placements} />
      )}
    </main>
  );
}
