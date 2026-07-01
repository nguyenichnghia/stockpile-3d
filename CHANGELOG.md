# Changelog

Tất cả thay đổi đáng kể của dự án được ghi tại đây.

Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.0.0/);
version theo [SemVer](https://semver.org/lang/vi/).

## [Unreleased]

## [0.3.0] - 2026-07-01
Giai đoạn 3 — Thiết lập kho + Tra cứu trực quan + Heatmap "sức khỏe kho".

### Added
- **Bộ sinh kho theo lưới (Grid Generator)** — `POST /api/warehouse/generate` nhận tham số lưới (zones/aisles/racks/levels/bins + kích thước bin + aisleGap + accessFace) và sinh hàng loạt `location` với tọa độ tính sẵn (bin theo x, level chồng theo z, aisle tách theo y), ghi trong một transaction. Chặn khi kho đã có dữ liệu. Chỉ tạo `location`, không đụng ledger. Package `com.stockpile.setup`. Tài liệu: `docs/warehouse-setup.md`.
- **Tra cứu/định vị mã hàng (SKU)** — `GET /api/lots/locate?sku={code}` trả mọi placement của lô thuộc SKU (case-insensitive). 3D làm nổi bật các lô khớp, làm mờ phần còn lại, kèm nhãn mã ô nổi trên lô khớp.
- **Tra cứu theo mã ô (bin)** — `GET /api/locations/locate?code={zone-aisle-rack-level-bin}` trả ô theo mã (kể cả ô trống, `found=false` nếu không có). 3D tô sáng khung ô + nhãn, dù ô trống.
- **Heatmap toàn kho** — `GET /api/heatmap?metric={fill|blocking|expiry}` trả giá trị [0,1] cho mỗi ô; 3D tô màu cả kho theo thang xanh→đỏ, kèm chú thích. `fill` = mức đầy (nhị phân), `blocking` = độ bị chặn (tái dùng `BlockingGraph`, chuẩn hóa theo cap=3), `expiry` = độ gấp FEFO theo hạn dùng (horizon 30 ngày). Package `com.stockpile.heatmap`.
- Test (Testcontainers + unit thuần): `WarehouseGeneratorServiceTest`, `LocateServiceTest`, `HeatmapServiceTest`, `HeatmapExpiryTest`, `PutawayScorerTest`, `RelocationPlannerTest`.

### Changed
- **Tách logic thuật toán khỏi DB để test không cần Docker:** trích `PutawayScorer` (SLAP scoring thuần) khỏi `PutawayService` và `RelocationPlanner` (heuristic CRP thuần) khỏi `RelocationService` — cùng kiểu tách như `BlockingGraph`. Service giữ I/O, ủy thác phần tính toán. Hành vi không đổi; bổ sung tầng test thuần chạy mili-giây không cần Postgres.

## [0.2.0] - 2026-06-22
Giai đoạn 2 — Lõi thuật toán: Relocation Engine (CRP) + Putaway Engine (SLAP).

### Added
- **Relocation Engine (CRP)** — Giai đoạn 2, lõi giá trị: `RelocationService.plan(lotId)` tính chuỗi di chuyển tối thiểu (heuristic greedy) để giải phóng một lô bị chặn; `BlockingGraph` (logic blocking thuần) + `LotBox`. Chỉ đề xuất, không ghi ledger.
- API `GET /api/relocation-plan?lotId={id}` trả `RelocationPlan` (các bước dời).
- ADR-0001 (chọn greedy heuristic cho CRP), `docs/algorithm-spec.md` (đặc tả đầy đủ), `docs/dev-log.md`.
- Repository queries: `Placement.findByBin_LaneId`, `Location.findEmptyInLane` / `findEmpty`.
- Test (Testcontainers + unit thuần): `BlockingGraphTest`, `RelocationServiceTest`.
- **Putaway Engine (SLAP)** — Giai đoạn 2: `PutawayService.suggest(lotId)` chấm điểm các vị trí trống (distToDock + blockingPenalty + FEFO-theo-z + fitPenalty, lọc cứng theo kích thước) và đề xuất vị trí điểm thấp nhất. Trọng số cấu hình qua `app.putaway.*`. Chỉ đề xuất.
- API `GET /api/putaway-suggestion?lotId={id}` trả `PutawaySuggestion` (vị trí khuyến nghị + ứng viên xếp hạng).
- `docs/algorithm-spec.md` bổ sung Phần B (SLAP); `PutawayServiceTest` (Testcontainers).

### Changed
- Luật blocking on-top dùng `>=` (lô xếp khít = chặn) thay vì `>`; vẫn giữ "2 lô cùng tầng không chặn" nhờ kiểm overlap (x,y). Xem Dev Log 2026-06-22.

## [0.1.0] - 2026-06-21
MVP — Giai đoạn 1: hiển thị kho 3D từ dữ liệu thật.

### Added
- Bộ tài liệu nền (`docs/00`–`03`), từ điển thuật ngữ (`docs/glossary.md`), sơ đồ UML (`docs/diagrams.md` + Astah), `.gitignore`, `CHANGELOG`, `README`.
- Công cụ quy trình: slash command `/ship`, kế hoạch commit theo giai đoạn (`docs/commit-plan.md`).
- ADR-0002 (backend stack Java + Spring Boot), ADR-0003 (movement ledger append-only + placement là projection).
- Khung backend Spring Boot (`src/backend`): Java 25, Maven, package theo feature phân tầng controller→service→repository; cấu hình DB đọc từ biến môi trường; Flyway; springdoc-openapi; health endpoint `GET /api/health`.
- Khung frontend Next.js + TypeScript (`src/frontend`).
- Data model lõi (Flyway `V1`): 5 bảng `location`, `sku`, `lot`, `placement`, `movement` (ledger append-only), PK BIGINT identity, enum VARCHAR + CHECK, index theo lane; JPA entities + Spring Data repositories.
- Projection ledger→placement: `MovementService` (ghi bút toán append-only + cập nhật placement) và `PlacementProjectionService` (`apply` + `rebuildAll` replay từ ledger). Pose placement = tọa độ góc bin (v1).
- REST API tồn kho: CRUD cho `sku`, `location`, `lot`; `GET /api/placements` (read-only projection); `POST /api/movements` (ghi ledger). DTO tách entity, Bean Validation, xử lý lỗi 404/400 tập trung; CORS cho frontend dev.
- 3D viewer: React Three Fiber + Three.js render kho bằng `InstancedMesh` (khung kệ từ `location`, lô từ `placement`), OrbitControls. Chỉ hiển thị, không ra quyết định.
- Test (Testcontainers + MockMvc): migration, projection (gồm ca khẳng định incremental == ledger replay), và API tồn kho.
