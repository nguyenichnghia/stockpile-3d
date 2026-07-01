"use client";

import { useCallback, useLayoutEffect, useMemo, useRef } from "react";
import { Canvas } from "@react-three/fiber";
import { OrbitControls, GizmoHelper, GizmoViewport, Grid, Html } from "@react-three/drei";
import * as THREE from "three";

import type { Location, Placement } from "@/lib/api";

/** Human-readable bin code, e.g. "A-01-00-1-01". */
function binCode(l: Location): string {
  return [l.zone, l.aisle, l.rack, l.level, l.bin].join("-");
}

/** Green (cool, value 0) → red (hot, value 1) via yellow. */
function heatColor(value: number): THREE.Color {
  const v = Math.max(0, Math.min(1, value));
  // hue 120° (green) down to 0° (red); THREE hue is 0..1.
  return new THREE.Color().setHSL((1 - v) * (120 / 360), 0.75, 0.5);
}

/** Bins colored per-instance by a heatmap value (setColorAt). */
function HeatmapBins({
  matrices,
  colors,
}: {
  matrices: THREE.Matrix4[];
  colors: THREE.Color[];
}) {
  const ref = useRef<THREE.InstancedMesh>(null);

  useLayoutEffect(() => {
    const mesh = ref.current;
    if (!mesh) return;
    matrices.forEach((m, i) => {
      mesh.setMatrixAt(i, m);
      mesh.setColorAt(i, colors[i]);
    });
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
  }, [matrices, colors]);

  return (
    <instancedMesh
      key={matrices.length}
      ref={ref}
      args={[undefined, undefined, Math.max(matrices.length, 1)]}
      frustumCulled
    >
      <boxGeometry args={[1, 1, 1]} />
      <meshStandardMaterial transparent opacity={0.55} />
    </instancedMesh>
  );
}

// Warehouse coords: x = width, y = depth, z = height.
// Three.js is Y-up, so we map warehouse z -> three Y and warehouse y -> three Z.
function centerMatrix(
  x: number,
  y: number,
  z: number,
  w: number,
  d: number,
  h: number,
): THREE.Matrix4 {
  const m = new THREE.Matrix4();
  m.compose(
    new THREE.Vector3(x + w / 2, z + h / 2, y + d / 2),
    new THREE.Quaternion(),
    new THREE.Vector3(w, h, d),
  );
  return m;
}

/** One InstancedMesh whose per-instance transforms come from `matrices`. */
function Instances({
  matrices,
  color,
  opacity = 1,
  wireframe = false,
}: {
  matrices: THREE.Matrix4[];
  color: string;
  opacity?: number;
  wireframe?: boolean;
}) {
  const ref = useRef<THREE.InstancedMesh>(null);

  useLayoutEffect(() => {
    const mesh = ref.current;
    if (!mesh) return;
    matrices.forEach((m, i) => mesh.setMatrixAt(i, m));
    mesh.instanceMatrix.needsUpdate = true;
  }, [matrices]);

  // key forces a fresh mesh when the instance count changes.
  return (
    <instancedMesh
      key={matrices.length}
      ref={ref}
      args={[undefined, undefined, Math.max(matrices.length, 1)]}
      frustumCulled
    >
      <boxGeometry args={[1, 1, 1]} />
      <meshStandardMaterial
        color={color}
        transparent={opacity < 1}
        opacity={opacity}
        wireframe={wireframe}
      />
    </instancedMesh>
  );
}

export default function Warehouse3D({
  locations,
  placements,
  highlightedBinIds,
  heatmap,
}: {
  locations: Location[];
  placements: Placement[];
  /** Bins to keep bright; when non-empty, all other lots are dimmed. */
  highlightedBinIds?: Set<number>;
  /** When set, color every bin by its value in [0,1] instead of drawing lots. */
  heatmap?: Map<number, number>;
}) {
  const binMatrices = useMemo(
    () => locations.map((l) => centerMatrix(l.x, l.y, l.z, l.w, l.d, l.h)),
    [locations],
  );

  const heatmapOn = !!heatmap;
  const heatColors = useMemo(
    () =>
      heatmap
        ? locations.map((l) => heatColor(heatmap.get(l.id) ?? 0))
        : [],
    [locations, heatmap],
  );

  // A lot is drawn slightly smaller than its bin so it reads as "inside".
  const byId = useMemo(
    () => new Map(locations.map((l) => [l.id, l])),
    [locations],
  );
  const lotMatrix = useCallback(
    (p: Placement): THREE.Matrix4 => {
      const bin = byId.get(p.binId);
      const s = 0.8;
      const w = bin ? bin.w * s : 0.8;
      const d = bin ? bin.d * s : 0.8;
      const h = bin ? bin.h * s : 0.8;
      return centerMatrix(p.x, p.y, p.z, w, d, h);
    },
    [byId],
  );

  // With no active search, every lot is drawn normally. With a search, split
  // lots into matched (bright) and unmatched (dimmed) so the results stand out.
  const searching = !!highlightedBinIds && highlightedBinIds.size > 0;
  const matchedMatrices = useMemo(
    () =>
      (searching
        ? placements.filter((p) => highlightedBinIds!.has(p.binId))
        : placements
      ).map(lotMatrix),
    [placements, highlightedBinIds, searching, lotMatrix],
  );
  const dimmedMatrices = useMemo(
    () =>
      searching
        ? placements.filter((p) => !highlightedBinIds!.has(p.binId)).map(lotMatrix)
        : [],
    [placements, highlightedBinIds, searching, lotMatrix],
  );

  // Labels only for matched lots (few of them), floating above each box, so the
  // user can read which bin a result sits in. Cheap: no labels when not searching.
  const labels = useMemo(() => {
    if (!searching) return [];
    return placements
      .filter((p) => highlightedBinIds!.has(p.binId))
      .map((p) => {
        const bin = byId.get(p.binId);
        if (!bin) return null;
        // Three.js position: warehouse (x,y,z) -> (x, z, y); float above the top.
        const pos: [number, number, number] = [
          p.x + bin.w / 2,
          p.z + bin.h + 0.4,
          p.y + bin.d / 2,
        ];
        return { key: p.binId, pos, text: binCode(bin) };
      })
      .filter((l): l is { key: number; pos: [number, number, number]; text: string } => l !== null);
  }, [placements, highlightedBinIds, searching, byId]);

  return (
    <Canvas camera={{ position: [20, 20, 20], fov: 50 }} style={{ background: "#0b1020" }}>
      <ambientLight intensity={0.6} />
      <directionalLight position={[10, 20, 10]} intensity={1.2} />
      <Grid args={[100, 100]} cellColor="#1e2a4a" sectionColor="#33406b" infiniteGrid fadeDistance={120} />

      {heatmapOn ? (
        /* Heatmap mode: color every bin by its metric value; lots are hidden. */
        <HeatmapBins matrices={binMatrices} colors={heatColors} />
      ) : (
        <>
          {/* Bins: translucent frames showing the slot grid. */}
          <Instances matrices={binMatrices} color="#5b6cff" opacity={0.18} wireframe />
          {/* Matched lots (or all lots when not searching): solid, bright. */}
          <Instances matrices={matchedMatrices} color="#ffb347" />
          {/* Unmatched lots during a search: dimmed so results stand out. */}
          <Instances matrices={dimmedMatrices} color="#ffb347" opacity={0.12} />
        </>
      )}

      {/* Bin-code labels above matched lots (only while searching, not in heatmap).
          Fixed small size (no distanceFactor so they don't balloon up close). */}
      {!heatmapOn && labels.map((l) => (
        <Html key={l.key} position={l.pos} center zIndexRange={[10, 0]}>
          <div
            style={{
              padding: "1px 4px",
              borderRadius: 3,
              background: "rgba(11,16,32,0.7)",
              color: "#ffd9a0",
              border: "1px solid rgba(255,179,71,0.7)",
              fontFamily: "system-ui, sans-serif",
              fontSize: 9,
              lineHeight: 1.3,
              whiteSpace: "nowrap",
              pointerEvents: "none",
            }}
          >
            {l.text}
          </div>
        </Html>
      ))}

      <OrbitControls makeDefault />
      <GizmoHelper alignment="bottom-right" margin={[80, 80]}>
        <GizmoViewport />
      </GizmoHelper>
    </Canvas>
  );
}
