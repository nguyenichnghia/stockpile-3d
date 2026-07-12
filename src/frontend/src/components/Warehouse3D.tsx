"use client";

import { useCallback, useLayoutEffect, useMemo, useRef } from "react";
import { Canvas } from "@react-three/fiber";
import { OrbitControls, GizmoHelper, GizmoViewport, Grid, Html, Line } from "@react-three/drei";
import * as THREE from "three";

import { binCode, type Location, type Placement, type PickStepKind } from "@/lib/api";

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

  // An InstancedMesh must have >= 1 instance, and an instance that never got a
  // setMatrixAt keeps the identity matrix — a ghost 1×1×1 box at the origin.
  // So render nothing at all when there is nothing to draw.
  if (matrices.length === 0) return null;

  return (
    <instancedMesh
      key={matrices.length}
      ref={ref}
      args={[undefined, undefined, matrices.length]}
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

  // Same ghost-instance guard as HeatmapBins: an empty list must draw nothing,
  // not one identity-matrix box at the origin.
  if (matrices.length === 0) return null;

  // key forces a fresh mesh when the instance count changes.
  return (
    <instancedMesh
      key={matrices.length}
      ref={ref}
      args={[undefined, undefined, matrices.length]}
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

/** Center of a bin in three.js coords (warehouse z is up → three Y). */
function binCenter(l: Location): [number, number, number] {
  return [l.x + l.w / 2, l.z + l.h / 2, l.y + l.d / 2];
}

/** A bright wireframe around one bin plus a floating label, shown even if empty. */
function BinMarker({
  bin,
  color,
  labelColor,
  text,
}: {
  bin: Location;
  color: string;
  labelColor: string;
  text: string;
}) {
  return (
    <>
      <Instances
        matrices={[centerMatrix(bin.x, bin.y, bin.z, bin.w, bin.d, bin.h)]}
        color={color}
        opacity={0.9}
        wireframe
      />
      <Html
        position={[bin.x + bin.w / 2, bin.z + bin.h + 0.4, bin.y + bin.d / 2]}
        center
        zIndexRange={[10, 0]}
      >
        <div
          style={{
            padding: "1px 4px",
            borderRadius: 3,
            background: "rgba(11,16,32,0.75)",
            color: labelColor,
            border: `1px solid ${color}`,
            fontFamily: "system-ui, sans-serif",
            fontSize: 9,
            whiteSpace: "nowrap",
            pointerEvents: "none",
          }}
        >
          {text}
        </div>
      </Html>
    </>
  );
}

/** The current pick-list step as shown on the scene. */
export type PlanStepMarker = {
  kind: PickStepKind;
  fromBinId: number;
  toBinId: number | null;
};

export default function Warehouse3D({
  locations,
  placements,
  highlightedBinIds,
  highlightedLocationBinId,
  heatmap,
  planStep,
}: {
  locations: Location[];
  placements: Placement[];
  /** Bins to keep bright; when non-empty, all other lots are dimmed. */
  highlightedBinIds?: Set<number>;
  /** A single bin (located by code) whose frame is highlighted, even if empty. */
  highlightedLocationBinId?: number | null;
  /** When set, color every bin by its value in [0,1] instead of drawing lots. */
  heatmap?: Map<number, number>;
  /** Current step of an active pick-list: marks from/to bins and links them. */
  planStep?: PlanStepMarker | null;
}) {
  const binMatrices = useMemo(
    () => locations.map((l) => centerMatrix(l.x, l.y, l.z, l.w, l.d, l.h)),
    [locations],
  );

  // The located bin (by code): a bright frame + label, drawn even when empty.
  const locatedBin = useMemo(
    () =>
      highlightedLocationBinId != null
        ? locations.find((l) => l.id === highlightedLocationBinId)
        : undefined,
    [locations, highlightedLocationBinId],
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

  // Bins of the active pick-list step (marked even when a bin is empty).
  const planFrom = planStep ? byId.get(planStep.fromBinId) : undefined;
  const planTo =
    planStep?.toBinId != null ? byId.get(planStep.toBinId) : undefined;
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
  // Skipped while a plan step is shown — its BinMarkers already label those bins.
  const labels = useMemo(() => {
    if (!searching || planStep) return [];
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
  }, [placements, highlightedBinIds, searching, byId, planStep]);

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

      {/* A bin located by code: a bright frame + label, shown even if empty. */}
      {locatedBin && (
        <BinMarker
          bin={locatedBin}
          color="#37e0a0"
          labelColor="#9affd8"
          text={binCode(locatedBin)}
        />
      )}

      {/* Active pick-list step: mark the source bin (and, for a relocation,
          the destination bin plus a dashed link) so the user sees exactly
          which move they are about to confirm. */}
      {planFrom && (
        <>
          <BinMarker
            bin={planFrom}
            color="#ffb347"
            labelColor="#ffd9a0"
            text={`${planStep!.kind === "PICK" ? "Lấy" : "Dời đi"} · ${binCode(planFrom)}`}
          />
          {planTo && (
            <>
              <BinMarker
                bin={planTo}
                color="#37e0a0"
                labelColor="#9affd8"
                text={`Đến · ${binCode(planTo)}`}
              />
              <Line
                points={[binCenter(planFrom), binCenter(planTo)]}
                color="#ffd9a0"
                lineWidth={1.5}
                dashed
                dashSize={0.3}
                gapSize={0.15}
              />
            </>
          )}
        </>
      )}

      {/* Clamp the orbit above the floor: from underneath, grounded lots appear
          to float over the grid — it reads as a rendering bug. */}
      <OrbitControls makeDefault maxPolarAngle={Math.PI / 2 - 0.05} />
      <GizmoHelper alignment="bottom-right" margin={[80, 80]}>
        <GizmoViewport />
      </GizmoHelper>
    </Canvas>
  );
}
