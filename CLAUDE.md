# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state: full roadmap (phases 1–4) complete, incl. multi-warehouse (tags v0.1.0 → v1.0.0)

This is **no longer documentation-only** — a working backend and frontend exist. Do not run `git init`, recreate the folder structure, or treat the layout as merely a *target* — it exists.

What is **built**:

- **Backend** (`src/backend/`, Java + Spring Boot): the domain entities (`Warehouse`, `Location`, `Sku`, `Lot`, `Placement`, `Movement`), inventory CRUD controllers, the append-only movement ledger with the `placement` projection (`PlacementProjectionService`, incremental + `rebuildAll` replay), the **CRP relocation engine** (`RelocationService` + pure `RelocationPlanner`/`BlockingGraph`), the **SLAP putaway engine** (`PutawayService` + pure `PutawayScorer`), the **picking engine** (`PickingService` + pure `PickPlanner`, FEFO/FIFO, relocations interleaved; `PickOrder`/`OrderLine`, Flyway `V2`), the **grid warehouse generator** (`com.stockpile.setup`, `POST /api/warehouses/{id}/generate` with a per-warehouse emptiness guard), **locate + heatmap** (`com.stockpile.heatmap`), the **realtime layer** (`com.stockpile.realtime`, STOMP `/ws`, `PlacementDelta` to `/topic/warehouse/{warehouseId}/lane/{laneId}` after commit), the **scan resolver** (`com.stockpile.scan`, `GET /api/scan` — v1 barcodes are derived ids per ADR-0007; bin codes resolve within the selected warehouse, `LOT-{id}` stays global; ledger records `scanRef`, and a warehouse with `require_scan` on (Flyway `V4`, toggled via `PATCH /api/warehouses/{id}`) rejects movements whose `scanRef` is missing or not the movement's lot barcode — flag off keeps the v1 encourage-and-audit contract), **reporting aggregates** (`com.stockpile.reporting`, `/api/reports/*`), the **what-if layout simulator** (`com.stockpile.whatif`, in-memory composition of grid builder + `PutawayScorer` + `BlockingGraph`, ADR-0008) and **multi-warehouse** (ADR-0009, Flyway `V3`): independent warehouses in one DB — every read takes a required `warehouseId` (relocation-plan/pick-plan derive it from the lot/order), `movement.warehouse_id` is NOT NULL (derived from bins, explicit for bin-less staging INBOUND; cross-warehouse movements are rejected — no transfers in v1), `pick_order` belongs to one warehouse, bin codes are unique per warehouse. Flyway migrations `V1`–`V4`. Tests use Testcontainers (real Postgres) — so `mvnw test` needs Docker running; only the pure `*PlannerTest`/`*ScorerTest`/`BlockingGraphTest` run without it.
- **Frontend** (`src/frontend/`, Next.js + React Three Fiber): the 3D viewer (`Warehouse3D.tsx`, `InstancedMesh`) plus SKU locate and a scan box (bin codes and `LOT-…`) with highlight+dim, heatmaps, a live STOMP subscription (`lib/realtime.ts`, warehouse-qualified topics), pick-list step-through (`PickPlanPanel.tsx`) where **scanning the lot barcode is the primary confirmation** (manual confirm records `scanRef=null`; hidden entirely when the warehouse enforces scanning) and each step is executed only on explicit user confirmation via `POST /api/movements` — the scene itself never decides; a `/reports` dashboard (KPI tiles, stacked movement-throughput chart, what-if form); and a **warehouse selector** — the working warehouse is the `?wh={id}` search param on `/` and `/reports`, defaulting to the first warehouse.
- **Infra:** `docker-compose.yml`, Dockerfiles for both, `CHANGELOG.md`, nine ADRs.

Roadmap is fully built (server-side scan enforcement, the slice ADR-0007 left room for, landed post-v1.0.0); remaining known slices (no ADR needed unless stated): a per-warehouse timezone for reports (currently UTC), policy what-if (vary SLAP weights instead of layout), cross-warehouse transfers (needs its own ADR per ADR-0009). Known local quirk: a stale production `.next` makes `next dev` 404 — delete `.next` before `npm run dev` after a build.

The docs remain the single source of truth for product direction and process. Read them before non-trivial work:

- [docs/00-index.md](docs/00-index.md) — table of contents + canonical repo structure (do not redefine it elsewhere).
- [docs/01-overview.md](docs/01-overview.md) — problem, data model, NFRs, architecture, algorithm formulations + Big-O, roadmap. Read for *what* and *why*.
- [docs/warehouse-setup.md](docs/warehouse-setup.md) — how to create `location` data; the grid generator (`POST /api/warehouses/{id}/generate`) is built, CSV import / editor are not.
- [docs/02-git-workflow.md](docs/02-git-workflow.md) — branching, Conventional Commits, repo structure, README checklist.
- [docs/03-documentation.md](docs/03-documentation.md) — ADR (Nygard) format, Keep a Changelog, Dev Log, Algorithm Spec conventions.

## What this project is

**Stockpile-3D** is a **decision-support system with a 3D interface** for warehouse management — *not* a 3D viewer. The 3D layer (React Three Fiber / Three.js) only visualizes and confirms decisions; an optimization engine in the backend makes them. The core value is the algorithms, not the rendering.

Target scale: medium warehouses, 10k–50k locations, ~5k–20k active lots. Positioned for block-stacking / deep-storage warehouses, not enterprise WMS competitors.

### The two core algorithms (the value of the project)

- **CRP — Container/Block Relocation Problem** (`Relocation Engine`): given a target lot to retrieve, compute the minimal sequence of moves to free it. NP-hard. v1 uses a **greedy heuristic** operating on a per-lane blocking graph; target < 500 ms for a lane of ≤ 100 lots. Output is a step list `(lot, from_bin, to_bin)` rendered as a 3D animation. See `01` §8.3.
- **SLAP — Storage Location Assignment Problem** (`Putaway Engine`): score candidate empty bins and pick the lowest-cost one. Greedy scoring, `O(F·k)`. See `01` §8.2.

### Architectural invariants (do not violate without an ADR)

- **Movement ledger is append-only and is the source of truth.** Every physical change (inbound, putaway, relocate, pick, outbound) is a ledger entry. The `placement` table (current state of each lot) is a **projection** rebuilt from the ledger. When data conflicts, the ledger wins.
- **The "blocking" relationship is the heart of the system.** Lot B blocks A if freeing A requires moving B first (on-top: `B.z_min >= A.z_max` with overlapping `(x,y)` footprint — flush stacking counts; in-front: same lane, B nearer the `access_face` covering A's exit path). These relationships form a directed blocking graph (a DAG when stacking is valid). Edge case (see `BlockingGraph.java` javadoc + Dev Log `03`, 2026-06-25): two lots at the *same level* must NOT block — the on-top `z` test uses `>=` but the `(x,y)`-footprint `overlaps()` check (strict `<`, touching edges don't overlap) is what separates "stacked" from "side by side". Do not "fix" the `>=` to `>` without re-reading that reasoning.
- **Blocking is local to a lane/stack.** Decided against PostGIS / global 3D spatial index — partition by `(warehouse_id, lane_id)` and reason within the lane (a few dozen lots). Reviewed and kept at multi-warehouse (ADR-0009): a lane always lives inside one warehouse, so the reasoning is unchanged; revisit a spatial index only if a single warehouse far exceeds the target scale.
- **3D never makes decisions.** Users do not drag-and-drop lots; the engine proposes, the 3D layer presents and the user confirms.

### Stack

Frontend: Next.js + React Three Fiber, using `InstancedMesh` + frustum culling to hit ~60 fps at ~50k instances (in place). Backend: **Java + Spring Boot** — Spring Web (REST commands/queries) and the Spring WebSocket/STOMP delta push to the scene (ADR-0005) both in place. Spring Data JPA/Hibernate for ORM (native queries reserved for heavy blocking lookups), Flyway for schema migrations (see [docs/adr/0002-backend-spring-boot.md](docs/adr/0002-backend-spring-boot.md)). Data: PostgreSQL (Supabase), database `stockpile_3d`. `src/` splits into `backend/` and `frontend/`. Note: because backend is Java and frontend is TypeScript, there is no shared-TS-types folder — generate a TS client from the backend's OpenAPI spec instead (per ADR-0002).

## Conventions to follow

These come from `02` and `03` and are already in force (code exists):

- **Git:** `main` stays buildable. One branch per feature/fix (`feature/xxx`, `fix/xxx`), merged via PR. **Conventional Commits** (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`) with lowercase, imperative descriptions. Small, single-purpose commits.
- **Workflow tooling:** full git workflow in [docs/02-git-workflow.md](docs/02-git-workflow.md); finalize a feature with the `/ship` command; phased commit plan in [docs/commit-plan.md](docs/commit-plan.md).
- **ADRs are immutable once `Accepted`.** To change a decision, write a new ADR that supersedes the old one — never edit an accepted ADR. Five sections (Nygard): Title · Status · Context · Decision · Consequences (state trade-offs, don't hide them). Numbered sequentially in `docs/adr/`. The greedy-CRP decision is ADR-0001 (template in `03` §3).
- **CHANGELOG** follows Keep a Changelog + SemVer; keep an `[Unreleased]` section and tag releases (`v0.1.0` = MVP, etc.).
- **Dev Log** captures real problems + root cause + fix at end of a session when something noteworthy happened.
- **Algorithm Spec** (`docs/algorithm-spec.md`) documents a finished algorithm module — problem, why it's hard (with academic citation), pseudocode, Big-O, trade-offs, concrete test cases. CRP + SLAP are already written up there; extend it when adding a new engine.

## Language note

All existing docs are in **Vietnamese**, and keeping new docs in Vietnamese is acceptable and consistent with the project's intent. Code identifiers and Conventional Commit messages are in English (see examples in `02` §3). Backend code is **Java** (Spring Boot); frontend code is **TypeScript**. Match the language of the file you are editing.

## Clarification policy

Ask before coding when a request is ambiguous **and the ambiguity is
consequential** — it affects business rules, the data model/schema,
architecture, or anything hard to reverse. In those cases, do not guess.

- Read the relevant docs (`docs/01`–`03`) and the existing code (`src/backend`, `src/frontend`) first, then ask
  **one focused question** via the **AskUserQuestion** tool: 2–4 concrete
  options, state the trade-off of each, mark a recommended one. Plan Mode is
  the natural place for this.
- For **low-stakes, reversible** choices (naming, file layout, formatting),
  don't interrupt — pick a sensible default and state the assumption inline
  so I can correct it.
- A choice that would violate an architectural invariant needs an **ADR**,
  not just a question (see §"Architectural invariants").
- Never invent business requirements. If a rule isn't in the docs and can't
  be inferred, ask rather than assume.

## Commit policy

- Propose a commit after each coherent, single-purpose unit of work that
  leaves the branch buildable — not per file edit, not one giant commit at
  the end.
- Use Conventional Commits (feat/fix/docs/refactor/test/chore), lowercase
  imperative. If the message needs "and", split into two commits.
- Do NOT commit without my confirmation. Never commit secrets, .env, or
  generated artifacts (node_modules/, target/, build/, .gradle/).
- Commit liberally on feature/* branches; main gets one squashed commit per
  feature at merge.
- Commits are authored solely as me — no Co-Authored-By or AI-generated
  attribution trailer. (Enforced by the `attribution` setting; this is a
  reminder.)