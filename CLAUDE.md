# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state: documentation-only, pre-code

This repository currently contains **only planning documents** (numbered `00`–`03`, written in Vietnamese). There is no source code, no `package.json`, no build system, no tests, and no git repository yet. Do not assume the directory structure described in the docs exists — it is the *target* layout, not the current one.

The docs are the single source of truth for product direction, architecture, and process. Read them before writing any code:

- [docs/00-index.md](docs/00-index.md) — table of contents; defines the canonical repo structure (do not redefine it elsewhere).
- [docs/01-overview.md](docs/01-overview.md) — problem, data model, NFRs, architecture, algorithm formulations + Big-O, roadmap. Read for *what* and *why*.
- [docs/02-git-workflow.md](docs/02-git-workflow.md) — branching, Conventional Commits, repo structure, README checklist.
- [docs/03-documentation.md](docs/03-documentation.md) — ADR (Nygard) format, Keep a Changelog, Dev Log, Algorithm Spec conventions.

When the project moves from planning to code, the first tasks (per `docs/00-index.md` §"Việc tiếp theo") are: init the git repo + `.gitignore` + the target folder structure, create a Postgres database named `stockpile_3d`, write `README.md`, then build the Phase 1 MVP on a `feature/` branch.

## What this project is

**Stockpile-3D** is a **decision-support system with a 3D interface** for warehouse management — *not* a 3D viewer. The 3D layer (React Three Fiber / Three.js) only visualizes and confirms decisions; an optimization engine in the backend makes them. The core value is the algorithms, not the rendering.

Target scale: medium warehouses, 10k–50k locations, ~5k–20k active lots. Positioned for block-stacking / deep-storage warehouses, not enterprise WMS competitors.

### The two core algorithms (the value of the project)

- **CRP — Container/Block Relocation Problem** (`Relocation Engine`): given a target lot to retrieve, compute the minimal sequence of moves to free it. NP-hard. v1 uses a **greedy heuristic** operating on a per-lane blocking graph; target < 500 ms for a lane of ≤ 100 lots. Output is a step list `(lot, from_bin, to_bin)` rendered as a 3D animation. See `01` §8.3.
- **SLAP — Storage Location Assignment Problem** (`Putaway Engine`): score candidate empty bins and pick the lowest-cost one. Greedy scoring, `O(F·k)`. See `01` §8.2.

### Architectural invariants (do not violate without an ADR)

- **Movement ledger is append-only and is the source of truth.** Every physical change (inbound, putaway, relocate, pick, outbound) is a ledger entry. The `placement` table (current state of each lot) is a **projection** rebuilt from the ledger. When data conflicts, the ledger wins.
- **The "blocking" relationship is the heart of the system.** Lot B blocks A if freeing A requires moving B first (on-top: `B.z_min ≥ A.z_max` with overlapping `(x,y)` projection; in-front: same lane, B nearer the `access_face` covering A's exit path). These relationships form a directed blocking graph (a DAG when stacking is valid). Note the documented edge case: use `>` not `≥` when distinguishing "same level" from "above" (see Dev Log in `03`, dated 2026-06-25).
- **Blocking is local to a lane/stack.** Decided against PostGIS / global 3D spatial index for v1 — partition by `(zone, aisle, rack)` and reason within the lane (a few dozen lots). Reconsider only for multi-warehouse.
- **3D never makes decisions.** Users do not drag-and-drop lots; the engine proposes, the 3D layer presents and the user confirms.

### Intended stack (from the docs)

Frontend: Next.js + React Three Fiber, using `InstancedMesh` + frustum culling to hit ~60 fps at ~50k instances. Backend: **Java + Spring Boot** — Spring Web (REST commands/queries) + Spring WebSocket (delta push to the scene), Spring Data JPA/Hibernate for ORM with native queries for the heavy blocking lookups, Flyway for schema migrations (see [docs/adr/0002-backend-spring-boot.md](docs/adr/0002-backend-spring-boot.md)). Data: PostgreSQL (Supabase), database `stockpile_3d`. Target structure splits `src/` into `backend/`, `frontend/`, and `shared/`. Note: because backend is Java and frontend is TypeScript, `src/shared` no longer holds shared TS types — generate a TS client from the backend's OpenAPI spec instead (per ADR-0002).

## Conventions to follow

These come from `02` and `03` and apply the moment code exists:

- **Git:** `main` stays buildable. One branch per feature/fix (`feature/xxx`, `fix/xxx`), merged via PR. **Conventional Commits** (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`) with lowercase, imperative descriptions. Small, single-purpose commits.
- **Workflow tooling:** full git workflow in [docs/02-git-workflow.md](docs/02-git-workflow.md); finalize a feature with the `/ship` command; phased commit plan in [docs/commit-plan.md](docs/commit-plan.md).
- **ADRs are immutable once `Accepted`.** To change a decision, write a new ADR that supersedes the old one — never edit an accepted ADR. Five sections (Nygard): Title · Status · Context · Decision · Consequences (state trade-offs, don't hide them). Numbered sequentially in `docs/adr/`. The greedy-CRP decision is ADR-0001 (template in `03` §3).
- **CHANGELOG** follows Keep a Changelog + SemVer; keep an `[Unreleased]` section and tag releases (`v0.1.0` = MVP, etc.).
- **Dev Log** captures real problems + root cause + fix at end of a session when something noteworthy happened.
- **Algorithm Spec** (`docs/algorithm-spec.md`) gets written when the algorithm module is done — problem, why it's hard (with academic citation), pseudocode, Big-O, trade-offs, concrete test cases.

## Language note

All existing docs are in **Vietnamese**, and keeping new docs in Vietnamese is acceptable and consistent with the project's intent. Code identifiers and Conventional Commit messages are in English (see examples in `02` §3). Backend code is **Java** (Spring Boot); frontend code is **TypeScript**. Match the language of the file you are editing.

## Clarification policy

Ask before coding when a request is ambiguous **and the ambiguity is
consequential** — it affects business rules, the data model/schema,
architecture, or anything hard to reverse. In those cases, do not guess.

- Read the relevant docs (`docs/01`–`03`) and existing code first, then ask
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