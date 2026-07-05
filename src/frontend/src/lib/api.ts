// Minimal client for the Stockpile-3D backend. Almost entirely read-only: the
// 3D scene only visualizes state; decisions live in the backend. The single
// write (`recordMovement`) is the user *confirming* an engine-proposed step —
// the scene itself never initiates a change.

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/** A physical warehouse site (ADR-0009); every read below is scoped to one. */
export type Warehouse = {
  id: number;
  code: string;
  name: string;
  /** When on, the backend rejects movements without a matching lot scan (ADR-0007). */
  requireScan: boolean;
  /** IANA zone id used to bucket reporting days (e.g. "Asia/Ho_Chi_Minh"). */
  timezone: string;
  createdAt: string;
};

export type Location = {
  id: number;
  warehouseId: number;
  zone: string;
  aisle: string;
  rack: string;
  level: string;
  bin: string;
  x: number;
  y: number;
  z: number;
  w: number;
  d: number;
  h: number;
  laneId: string;
  accessFace: string;
};

export type Placement = {
  id: number;
  lotId: number;
  binId: number;
  x: number;
  y: number;
  z: number;
};

/** Human-readable bin code, e.g. "A-01-00-1-01". */
export function binCode(l: Location): string {
  return [l.zone, l.aisle, l.rack, l.level, l.bin].join("-");
}

/**
 * Builds the error for a failed response. The backend's error handler returns
 * {@code {message, error, status, ...}} — surface {@code message} so the UI can
 * say "Lot 5 has no placement" instead of a bare "failed: 400".
 */
async function httpError(method: string, path: string, res: Response): Promise<Error> {
  let detail = res.statusText;
  try {
    const body = await res.json();
    if (typeof body?.message === "string" && body.message) {
      detail = body.message;
    }
  } catch {
    // Non-JSON body (proxy error page, empty body) — keep the status text.
  }
  return new Error(`${method} ${path}: ${res.status} ${detail}`.trim());
}

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, { cache: "no-store" });
  if (!res.ok) {
    throw await httpError("GET", path, res);
  }
  return res.json() as Promise<T>;
}

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw await httpError("POST", path, res);
  }
  return res.json() as Promise<T>;
}

export const fetchWarehouses = () => getJson<Warehouse[]>("/api/warehouses");

export const fetchLocations = (warehouseId: number) =>
  getJson<Location[]>(`/api/locations?warehouseId=${warehouseId}`);
export const fetchPlacements = (warehouseId: number) =>
  getJson<Placement[]>(`/api/placements?warehouseId=${warehouseId}`);

export type LocateMatch = {
  lotId: number;
  binId: number;
  x: number;
  y: number;
  z: number;
};

export type LocateResult = {
  sku: string;
  matchCount: number;
  matches: LocateMatch[];
};

/** Locate every placed lot of a SKU code (for highlight/dim on the 3D scene). */
export const locateBySku = (sku: string, warehouseId: number) =>
  getJson<LocateResult>(
    `/api/lots/locate?sku=${encodeURIComponent(sku)}&warehouseId=${warehouseId}`,
  );

export type ScanResult = {
  code: string;
  type: "LOT" | "BIN" | "UNKNOWN";
  found: boolean;
  /** Set when a LOT code resolved; binId/binCode/laneId null if unplaced. */
  lot: {
    id: number;
    sku: string;
    binId: number | null;
    binCode: string | null;
    laneId: string | null;
  } | null;
  /** Set when a BIN code resolved; lotIds empty for an empty bin. */
  bin: { id: number; code: string; laneId: string; lotIds: number[] } | null;
};

/**
 * Resolves a scanned (or typed) code — "LOT-{id}" or a full bin code
 * "zone-aisle-rack-level-bin" — to the lot or bin it names (ADR-0007). Bin
 * codes are only unique per warehouse, so the working warehouse scopes them.
 */
export const resolveScan = (code: string, warehouseId: number) =>
  getJson<ScanResult>(
    `/api/scan?code=${encodeURIComponent(code)}&warehouseId=${warehouseId}`,
  );

export type HeatmapCell = { binId: number; value: number };

export type HeatmapResult = {
  metric: string;
  cells: HeatmapCell[];
};

/** Per-bin heat values in [0,1] for coloring the whole warehouse by a metric. */
export const fetchHeatmap = (metric: string, warehouseId: number) =>
  getJson<HeatmapResult>(
    `/api/heatmap?metric=${encodeURIComponent(metric)}&warehouseId=${warehouseId}`,
  );

export type OrderLine = {
  id: number | null;
  skuId: number;
  qty: number;
};

export type Order = {
  id: number;
  code: string;
  warehouseId: number;
  status: string;
  createdAt: string;
  lines: OrderLine[];
};

export const fetchOrders = () => getJson<Order[]>("/api/orders");

export type PickStepKind = "RELOCATE" | "PICK";

/** One action in a pick-list; RELOCATE clears a blocker, PICK takes the lot (toBinId null). */
export type PickStep = {
  kind: PickStepKind;
  lotId: number;
  fromBinId: number;
  toBinId: number | null;
};

export type PickLineResult = {
  sku: string;
  requested: number;
  fulfilled: number;
  shortfall: number;
};

export type PickPlan = {
  orderId: number;
  lines: PickLineResult[];
  steps: PickStep[];
};

/** Proposed pick-list for an order: relocations interleaved with picks, in order. */
export const fetchPickPlan = (orderId: number) =>
  getJson<PickPlan>(`/api/pick-plan?orderId=${orderId}`);

export type MovementRequest = {
  lotId: number;
  type: PickStepKind;
  fromBin: number | null;
  toBin: number | null;
  /** Raw scanned code when the step was confirmed by scan; null = unscanned. */
  scanRef?: string | null;
};

export type Movement = MovementRequest & { id: number; ts: string };

/**
 * Records one confirmed step in the append-only ledger. The backend updates the
 * placement projection and pushes a realtime delta — the scene follows from that,
 * so callers should NOT patch local state themselves.
 */
export const recordMovement = (m: MovementRequest) =>
  postJson<Movement>("/api/movements", m);

/** Warehouse KPIs for the reporting dashboard (unit-based counts). */
export type ReportSummary = {
  totalBins: number;
  occupiedBins: number;
  fillRate: number;
  activeLots: number;
  blockedLots: number;
  expiringSoon: number;
  expired: number;
  openOrders: number;
  movementsToday: number;
  /** IANA zone id the day-based figures were bucketed in. */
  timezone: string;
};

export const fetchReportSummary = (warehouseId: number) =>
  getJson<ReportSummary>(`/api/reports/summary?warehouseId=${warehouseId}`);

/** Ledger throughput for one (local day, movement type); zero days emit no row. */
export type MovementDaily = { date: string; type: string; count: number };

export const fetchMovementsDaily = (days: number, warehouseId: number) =>
  getJson<MovementDaily[]>(
    `/api/reports/movements?days=${days}&warehouseId=${warehouseId}`,
  );

/** Grid parameters for a hypothetical layout (same shape the generator takes). */
export type GridSpec = {
  zones: number;
  aislesPerZone: number;
  racksPerAisle: number;
  levelsPerRack: number;
  binsPerLevel: number;
  binWidth: number;
  binDepth: number;
  binHeight: number;
  aisleGap: number;
  accessFace: string;
};

export type LayoutMetrics = {
  bins: number;
  placedLots: number;
  unplacedLots: number;
  blockedLots: number;
  fillRate: number;
  avgDistToDock: number;
};

export type WhatIfResult = { current: LayoutMetrics; simulated: LayoutMetrics };

/**
 * Simulates re-putting one warehouse's current stock into a hypothetical grid
 * (SLAP order, in-memory). Side-effect free despite the POST — nothing is
 * persisted.
 */
export const simulateLayout = (warehouseId: number, spec: GridSpec) =>
  postJson<WhatIfResult>(`/api/whatif/layout?warehouseId=${warehouseId}`, spec);
