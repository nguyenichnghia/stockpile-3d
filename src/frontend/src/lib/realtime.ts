// STOMP-over-WebSocket client for live placement updates. Kept separate from
// api.ts (which is HTTP read-only): this is the one place the scene subscribes
// to server-pushed deltas. The 3D layer still only presents — deltas originate
// from movements recorded through the REST/ledger write path.

import { Client, type IMessage } from "@stomp/stompjs";

const WS_URL =
  (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").replace(/^http/, "ws") +
  "/ws";

/** Mirror of the backend PlacementDelta (com.stockpile.realtime.dto). */
export type PlacementDelta = {
  kind: "UPSERT" | "REMOVE";
  lotId: number;
  binId: number | null;
  x: number | null;
  y: number | null;
  z: number | null;
  ts: string;
};

/**
 * Connects to the realtime endpoint and subscribes to the given lane topics.
 * Calls `onDelta` for each delta. Returns a disposer that closes the socket and
 * all subscriptions — call it from a React effect cleanup.
 */
export function connectPlacements(
  laneIds: string[],
  onDelta: (delta: PlacementDelta) => void,
): () => void {
  const client = new Client({
    brokerURL: WS_URL,
    reconnectDelay: 3000, // browser-side auto-reconnect
    onConnect: () => {
      for (const lane of laneIds) {
        client.subscribe(`/topic/lane/${lane}`, (msg: IMessage) => {
          onDelta(JSON.parse(msg.body) as PlacementDelta);
        });
      }
    },
  });
  client.activate();
  return () => {
    void client.deactivate();
  };
}

/** Applies a delta to a placements list, merging by lotId (add / replace / remove). */
export function applyDelta<T extends { lotId: number; binId: number; x: number; y: number; z: number }>(
  prev: T[],
  d: PlacementDelta,
  make: (d: PlacementDelta) => T,
): T[] {
  const without = prev.filter((p) => p.lotId !== d.lotId);
  if (d.kind === "REMOVE") return without;
  without.push(make(d));
  return without;
}
