// Minimal client for the Stockpile-3D backend. Read-only here: the 3D scene
// only visualizes state; it never mutates it (decisions live in the backend).

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

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, { cache: "no-store" });
  if (!res.ok) {
    throw new Error(`GET ${path} failed: ${res.status} ${res.statusText}`);
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
