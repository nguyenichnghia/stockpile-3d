// Minimal client for the Stockpile-3D backend. Almost entirely read-only: the
// 3D scene only visualizes state; decisions live in the backend. The single
// write (`recordMovement`) is the user *confirming* an engine-proposed step —
// the scene itself never initiates a change.

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type Location = {
  id: number;
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

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, { cache: "no-store" });
  if (!res.ok) {
    throw new Error(`GET ${path} failed: ${res.status} ${res.statusText}`);
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
    throw new Error(`POST ${path} failed: ${res.status} ${res.statusText}`);
  }
  return res.json() as Promise<T>;
}

export const fetchLocations = () => getJson<Location[]>("/api/locations");
export const fetchPlacements = () => getJson<Placement[]>("/api/placements");

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
export const locateBySku = (sku: string) =>
  getJson<LocateResult>(`/api/lots/locate?sku=${encodeURIComponent(sku)}`);

export type BinLocateResult = {
  code: string;
  found: boolean;
  binId: number | null;
  x: number | null;
  y: number | null;
  z: number | null;
};

/** Locate a single bin by its full code (zone-aisle-rack-level-bin). */
export const locateBin = (code: string) =>
  getJson<BinLocateResult>(`/api/locations/locate?code=${encodeURIComponent(code)}`);

export type HeatmapCell = { binId: number; value: number };

export type HeatmapResult = {
  metric: string;
  cells: HeatmapCell[];
};

/** Per-bin heat values in [0,1] for coloring the whole warehouse by a metric. */
export const fetchHeatmap = (metric = "fill") =>
  getJson<HeatmapResult>(`/api/heatmap?metric=${encodeURIComponent(metric)}`);

export type OrderLine = {
  id: number | null;
  skuId: number;
  qty: number;
};

export type Order = {
  id: number;
  code: string;
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
};

export type Movement = MovementRequest & { id: number; ts: string };

/**
 * Records one confirmed step in the append-only ledger. The backend updates the
 * placement projection and pushes a realtime delta — the scene follows from that,
 * so callers should NOT patch local state themselves.
 */
export const recordMovement = (m: MovementRequest) =>
  postJson<Movement>("/api/movements", m);
