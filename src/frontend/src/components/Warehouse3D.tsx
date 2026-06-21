"use client";

import { useLayoutEffect, useMemo, useRef } from "react";
import { Canvas } from "@react-three/fiber";
import { OrbitControls, GizmoHelper, GizmoViewport, Grid } from "@react-three/drei";
import * as THREE from "three";

import type { Location, Placement } from "@/lib/api";

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
}: {
  locations: Location[];
  placements: Placement[];
}) {
  const binMatrices = useMemo(
    () => locations.map((l) => centerMatrix(l.x, l.y, l.z, l.w, l.d, l.h)),
    [locations],
  );

  // A lot is drawn slightly smaller than its bin so it reads as "inside".
  const byId = useMemo(
    () => new Map(locations.map((l) => [l.id, l])),
    [locations],
  );
  const lotMatrices = useMemo(
    () =>
      placements.map((p) => {
        const bin = byId.get(p.binId);
        const s = 0.8;
        const w = bin ? bin.w * s : 0.8;
        const d = bin ? bin.d * s : 0.8;
        const h = bin ? bin.h * s : 0.8;
        return centerMatrix(p.x, p.y, p.z, w, d, h);
      }),
    [placements, byId],
  );

  return (
    <Canvas camera={{ position: [20, 20, 20], fov: 50 }} style={{ background: "#0b1020" }}>
      <ambientLight intensity={0.6} />
      <directionalLight position={[10, 20, 10]} intensity={1.2} />
      <Grid args={[100, 100]} cellColor="#1e2a4a" sectionColor="#33406b" infiniteGrid fadeDistance={120} />

      {/* Bins: translucent frames showing the slot grid. */}
      <Instances matrices={binMatrices} color="#5b6cff" opacity={0.18} wireframe />
      {/* Lots: solid boxes for what is physically placed. */}
      <Instances matrices={lotMatrices} color="#ffb347" />

      <OrbitControls makeDefault />
      <GizmoHelper alignment="bottom-right" margin={[80, 80]}>
        <GizmoViewport />
      </GizmoHelper>
    </Canvas>
  );
}
