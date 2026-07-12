# Changelog

Tất cả thay đổi đáng kể của dự án được ghi tại đây.

Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.0.0/);
version theo [SemVer](https://semver.org/lang/vi/).

## [Unreleased]

### Added
- **CI bằng GitHub Actions** (`.github/workflows/ci.yml`): mọi PR vào `main` (và push lên `main`) chạy hai job song song — backend `mvnw test` (đủ 110 test Testcontainers, runner `ubuntu-latest` có sẵn Docker) và frontend `npm ci` + lint + `next build` (build kiêm type-check vì frontend chưa có unit test). Tự động hóa bất biến "main luôn build pass" của [02 §2](docs/02-git-workflow.md) thay vì tin vào kỷ luật chạy test tay trước khi merge. Kèm fix nền: `mvnw` trước giờ được commit thiếu bit thực thi (mode `100644`) — checkout trên Linux sẽ "permission denied", trên Windows không bao giờ lộ ra.

### Fixed
- **Khối "ma" ở gốc tọa độ cảnh 3D**: `Instances`/`HeatmapBins` tạo `InstancedMesh` với `Math.max(length, 1)` instance — khi danh sách rỗng (ví dụ lớp lot-bị-dim lúc không tìm kiếm), instance duy nhất không bao giờ được `setMatrixAt` nên giữ ma trận identity → một khối 1×1×1 mờ đứng ở gốc tọa độ, lún nửa dưới sàn. Ít khi lộ vì SLAP thường xếp hàng vào ô gần dock (trùng gốc) che mất nó; kho có góc gốc trống là thấy ngay. Giờ danh sách rỗng render `null` — không vẽ gì cả. Kèm chỉnh UX cùng gốc rễ báo cáo: chặn OrbitControls xoay xuống dưới mặt sàn (`maxPolarAngle`) — nhìn từ dưới đất lên, kiện đứng trên sàn trông như lơ lửng trên lưới, rất giống lỗi render.
- Request body không đọc được (JSON hỏng, hoặc field không deserialize được — ví dụ enum sai `"accessFace":"FRONT"` khi generate) giờ trả 400 với `message` nêu tên field, giá trị sai và danh sách giá trị hợp lệ nếu là enum — nửa còn lại của fix #31 (query param): `HttpMessageNotReadableException` trước đây chưa có handler nên rơi về body mặc định của Spring không có `message`, thứ frontend hiển thị. Lưu ý kỹ thuật: web layer của Spring Boot 4 dùng **Jackson 3** (`tools.jackson`) — handler phải bắt `InvalidFormatException` của Jackson 3; bản `com.fasterxml` (Jackson 2) vẫn nằm trên classpath (springdoc kéo theo) nhưng import nhầm nó thì `instanceof` không bao giờ khớp.
- Trang chủ chạy qua Docker Compose luôn báo "Backend chưa sẵn sàng": fetch của server component chạy *bên trong* container frontend, nơi `localhost:8080` trỏ về chính nó chứ không phải backend. `api.ts` giờ phân biệt nơi chạy — phía server ưu tiên `API_URL_INTERNAL` (env runtime, compose đặt `http://backend:8080`), phía trình duyệt giữ `NEXT_PUBLIC_API_URL`. Chạy dev trên máy thật không đổi hành vi (fallback như cũ).

## [1.1.0] - 2026-07-06
Sau v1.0.0 (hoàn tất roadmap): các slice mở rộng — enforce quét phía server, timezone kho cho báo cáo, what-if theo chính sách, và **chuyển kho** (mục cuối ADR-0009 chừa lại).

### Added
- **Chuyển kho (cross-warehouse transfer, ADR-0010)** — chuyển một lô từ kho A sang kho B, hoàn tất mục roadmap cuối cùng còn treo. Dùng **Phương án B**: transfer = cặp bút toán ledger liên kết — `OUTBOUND` ở A khi mở (lô rời kho → in-transit, không placement) + `INBOUND` ở B khi nhận (đặt vào ô đích). **Không lật bất biến nào của ADR-0009**: mỗi bút toán vẫn thuộc đúng một kho nên `MovementService.validateWarehouse` giữ nguyên, ledger vẫn nguồn sự thật, vị trí lô vẫn suy từ ledger. Thực thể `Transfer` (Flyway `V6`, package `com.stockpile.transfer`) chỉ *liên kết* hai movement + mang trạng thái chuyến (`IN_TRANSIT`→`COMPLETED`, v1 chưa có hủy). API: `POST /api/transfers` (mở, `{lotId,toWarehouseId}`), `POST /api/transfers/{id}/receive` (nhận, `{toBinId}`), `GET /api/transfers?toWarehouseId=` (danh sách đang đến). Guard: lô phải đang đặt (không staging/đang transfer khác), kho đích khác kho nguồn, ô đích thuộc kho đích, không nhận hai lần. Ô đích chọn thủ công (SLAP gợi ý ô ở B là slice sau). Kho bật `require_scan` (ADR-0007) sẽ từ chối transfer vì bút toán chưa mang scanRef — mặc định an toàn. Frontend `/reports`: mục "Hàng đang chuyển đến kho này" — danh sách lô in-transit + chọn ô đích + nút Nhận (ghi INBOUND qua ledger, scene cập nhật qua STOMP). Test: `TransferServiceTest` (open→in-transit→receive→completed + các guard).
- **What-if theo chính sách (trọng số SLAP)** — `POST /api/whatif/policy?warehouseId=` (ADR-0008, lấp đúng giới hạn "chưa mô phỏng thay đổi chính sách" mục Hệ quả): giữ nguyên **các ô thật** của kho, xếp lại toàn bộ lô hai lần — một với trọng số mặc định (`app.putaway.*`), một với trọng số client gửi — rồi so cạnh nhau. Layout cố định nên khác biệt là do trọng số, không lẫn với đổi bin (đối xứng với what-if layout: vary bin, giữ trọng số). `PutawayWeightsDto` nhận 4 trọng số tùy chọn (`@PositiveOrZero`, để trống → giữ mặc định của trọng số đó, cô lập được một trọng số); `WhatIfPolicyResult` trả cả hai `LayoutMetrics` + hai bộ trọng số đã dùng để UI hiện đúng thứ đã so. Thuần mô phỏng — tái dùng `simulatePutaway` chung với what-if layout, không ghi ledger/location/event. Trang `/reports` thêm mục "thử bộ trọng số SLAP khác": form 4 trọng số (placeholder = giá trị mặc định sau lần chạy đầu) + bảng so sánh Mặc định/Thử nghiệm (tái dùng `CompareRow`). Test: `WhatIfServiceTest` (đổi trọng số làm lô rơi vào ô khác trên cùng layout; baseline = default + echo trọng số).
- **Timezone theo kho cho reports** — cột `warehouse.timezone` (Flyway `V5`, IANA zone id, mặc định `'UTC'` — kho hiện có không đổi hành vi): các con số theo ngày của `/api/reports/*` ("movement hôm nay", cột throughput theo ngày) giờ gộp theo lịch địa phương của kho (`at time zone` trong query ledger) thay vì UTC; ledger vẫn lưu instant, chỉ cách chia rổ ngày thay đổi. Đặt lúc tạo kho hoặc `PATCH /api/warehouses/{id}` (`{"timezone":"Asia/Ho_Chi_Minh"}`, zone id sai → 400). `ReportSummary` trả thêm `timezone`; tile "Movement hôm nay" hiển thị múi giờ đang dùng thay nhãn "(UTC)" cứng. Reports của kho không tồn tại giờ trả 404 thay vì toàn số 0. Test: `ReportingServiceTest` (movement 01:00 giờ địa phương = 18:00 UTC hôm trước phải rơi vào ngày địa phương), `InventoryApiTest` (default UTC, PATCH, zone sai).
- **Enforce quét mã phía server (slice ADR-0007 chừa sẵn)** — cột `warehouse.require_scan` (Flyway `V4`, mặc định `false`): kho bật cờ thì `POST /api/movements` từ chối 400 khi `scanRef` thiếu hoặc không khớp mã lô `LOT-{lotId}` của movement (so khớp không phân biệt hoa thường, đúng pattern của resolver); kho tắt cờ giữ nguyên hợp đồng v1 "khuyến khích + audit". Áp cho **mọi** movement của kho, kể cả INBOUND staging không bin (§8.6 — mọi điểm chạm vật lý). `PATCH /api/warehouses/{id}` bật/tắt cờ (field bỏ trống giữ nguyên); `requireScan` có trong payload warehouse. Frontend: kho bắt buộc quét thì panel pick-list ẩn nút "Xác nhận không quét". Test: `ScanEnforcementTest` (Testcontainers).

### Fixed
- Lỗi thiếu query param bắt buộc (ví dụ gọi `/api/placements` không kèm `warehouseId`) hoặc param sai kiểu (`warehouseId=abc`) giờ trả 400 với `message` nêu tên param, cùng shape JSON với các lỗi khác — trước đó là body mặc định của Spring không có `message` (finding từ verify PR #28); frontend vốn hiển thị `message` nên giờ báo lỗi đọc được.

## [1.0.0] - 2026-07-04
Hoàn tất toàn bộ roadmap 4 giai đoạn — mảnh cuối: multi-warehouse.

### Added
- **Multi-warehouse (giai đoạn 4, ADR-0009)** — nhiều kho vật lý độc lập trong một CSDL, hoàn tất mục roadmap cuối cùng:
  - Thực thể `Warehouse` (`code` UNIQUE, `name`) + Flyway `V3`: `location/movement/pick_order` mang `warehouse_id NOT NULL`; mã ô 5 đoạn giờ UNIQUE **trong một kho**; dữ liệu cũ backfill vào kho mặc định `MAIN` (chỉ tạo khi DB có dữ liệu). API `GET/POST /api/warehouses`.
  - Generator theo kho: `POST /api/warehouses/{id}/generate` (thay `POST /api/warehouse/generate`); guard "đã có location" tính theo kho đích — tạo kho thứ hai là thao tác bình thường.
  - Xem lại quyết định spatial index (docs/01 §6): **giữ nguyên, thu hẹp phạm vi** — blocking vẫn cục bộ theo lane, khóa phân vùng thành `(warehouse_id, lane_id)`, vẫn không PostGIS. Ba lõi thuần `BlockingGraph`/`PutawayScorer`/`RelocationPlanner` không đổi.
  - Mọi API đọc nhận `warehouseId` (locations, placements, heatmap, scan, locate, reports, what-if, putaway-suggestion); relocation-plan/pick-plan suy kho từ dữ liệu. `LOT-{id}` vẫn toàn cục (ADR-0007 nguyên vẹn); mã ô resolve trong kho đang chọn.
  - Ledger enforce "một kho một bút toán": movement có hai bin khác kho bị từ chối (chưa có transfer); movement không bin (INBOUND staging) phải khai `warehouseId`. Topic STOMP thành `/topic/warehouse/{id}/lane/{laneId}`.
  - Frontend: bộ chọn kho qua `?wh=` trên `/` (link theo mã kho) và `/reports` (dropdown); pick-list chỉ hiện đơn của kho đang xem; subscription realtime theo kho. Test: 88/88 (6 test mới — scan phân biệt hai kho trùng mã ô, generate kho thứ hai, chặn movement xuyên kho, INBOUND staging bắt buộc khai kho, CRUD warehouse).

### Changed
- **Breaking:** các endpoint đọc yêu cầu `warehouseId`; đường generate đổi sang `/api/warehouses/{id}/generate`; topic STOMP đổi dạng — client cũ (trước v0.6.0) cần cập nhật. Frontend trong repo đã cập nhật cùng đợt.

## [0.6.0] - 2026-07-03
Giai đoạn 4 (phần phân tích) — Reporting dashboard + What-if layout simulation.

### Added
- **What-if layout simulation (giai đoạn 4)** — `POST /api/whatif/layout` (package `com.stockpile.whatif`; ADR-0008): dựng lưới giả định trong bộ nhớ (tách `buildGrid` thuần khỏi generator), xếp lại toàn bộ lô đang đặt bằng chính `PutawayScorer` (thứ tự expiry-trước, quyết định luôn), đo cả hai layout bằng chính `BlockingGraph` → so sánh số ô, lô xếp được/không còn chỗ, lô bị chặn, mức lấp đầy, khoảng cách tới dock. Thuần mô phỏng — không ghi ledger/location, không event; chặn tối đa 100k ô. Trang `/reports` thêm mục "What-if": form tham số lưới + bảng so sánh Hiện tại/Mô phỏng (delta tô màu khi biết chiều tốt/xấu). Test: `WhatIfServiceTest` (Testcontainers).
- **Reporting dashboard (giai đoạn 4)** — backend `com.stockpile.reporting` (read-only, cùng nguồn dữ liệu với scene): `GET /api/reports/summary` trả KPI kho (mức lấp đầy, số lô, **lô bị chặn** — tái dùng `BlockingGraph` theo lane, sắp/quá hạn theo horizon FEFO 30 ngày, đơn OPEN, movement hôm nay) và `GET /api/reports/movements?days=` (mặc định 14, tối đa 90) trả bút toán ledger theo (ngày UTC, loại). Frontend trang `/reports`: hàng thẻ KPI (icon + màu trạng thái khi có vấn đề) + biểu đồ cột chồng movement theo ngày (màu series cố định theo loại, đã kiểm tra CVD trên nền tối; tooltip hover, chú giải, kèm bảng số liệu), link qua lại với viewer 3D. Ngày tính theo UTC (v1 chưa có timezone kho — xem lại khi multi-warehouse). Test: `ReportingServiceTest` (Testcontainers).
Hoàn tất giai đoạn 3 — Vận hành trên 3D: chạy pick-list từng bước + quét mã vạch.

### Fixed
- Client API frontend hiển thị `message` lỗi chi tiết từ backend (ví dụ khi xác nhận bước pick-list thất bại vì lô đã bị lấy) thay vì chỉ mã trạng thái `400`.

### Added
- **Điểm chạm quét mã vạch (barcode scan)** — bước đầu của "ground-truth sync" (§8.6):
  mã vạch v1 suy diễn từ định danh sẵn có (`LOT-{id}` cho lô, mã ô 5 đoạn cho ô — không thêm cột schema; ADR-0007). `GET /api/scan?code=` (package `com.stockpile.scan`, read-only) phân giải mã quét → lô (kèm vị trí hiện tại) / ô (kèm lô đang chiếm) / không nhận dạng. Xác nhận bước pick-list giờ **quét mã lô là đường chính**: khớp `LOT-{lotId}` của bước mới ghi movement, kèm `scanRef` = mã quét thô vào ledger (cột `scan_ref` chờ từ V1 giờ có dữ liệu); vẫn cho xác nhận thủ công nhưng `scanRef=null` — audit phân biệt được. Ô "tra mã ô" trên viewer thành ô quét đa năng: nhận cả mã ô lẫn `LOT-…` (định vị lô, highlight ô chứa nó). Test: `ScanServiceTest` (Testcontainers).
- **Chạy pick-list trên 3D (xác nhận từng bước)** — chọn đơn hàng ngay trên viewer để lập pick-list (`GET /api/pick-plan`); panel bên phải liệt kê mức đáp ứng từng dòng (kèm cảnh báo `shortfall`) và chuỗi bước theo thứ tự. Bước hiện tại được đánh dấu trên scene: khung + nhãn ô nguồn ("Dời đi"/"Lấy"), ô đích ("Đến") và đường nối đứt nét khi là bước dời. Nút **Xác nhận bước** ghi movement (`POST /api/movements`, RELOCATE/PICK) — engine đề xuất, người dùng xác nhận, 3D không tự quyết; scene cập nhật qua chính kênh STOMP realtime, không vá state cục bộ. Bước lỗi (ví dụ lô đã bị người khác lấy) hiện thông báo và không nhảy bước. Component mới `PickPlanPanel`; không đổi backend.

## [0.4.0] - 2026-07-02
Giai đoạn 4 — Realtime + Picking Engine.

### Added
- **Picking Engine (đơn hàng → pick-list)** — thêm entity `PickOrder` + `OrderLine` (Flyway `V2`, bảng `pick_order` vì `order` là từ khóa SQL; `qty` đếm kiện/thùng/pallet) với CRUD `/api/orders`. `PickingService.plan(orderId)` (`GET /api/pick-plan?orderId=`) chọn lô cho từng dòng đơn và trả `PickPlan` gồm các bước theo thứ tự (relocation xen kẽ pick). Chọn lô là lõi thuần `PickPlanner`: chính sách dẫn (FEFO theo `expiry` sớm nhất, FIFO theo lô cũ nhất), phá hòa bằng ít-bị-chặn — hàng gần hết hạn không bị kẹt sau lô dễ hơn. Lô bị chặn được chèn sẵn các bước dời (tái dùng `RelocationService`/CRP) trước bước PICK → pick-list chạy được ngay. Báo `shortfall` khi thiếu hàng. Proposal-only (không ghi ledger). Package `com.stockpile.picking`; ADR-0006.
- Test (unit thuần + Testcontainers): `PickPlannerTest` (FEFO/FIFO, phá hòa least-blocked, hạn không thua ít-bị-chặn, shortfall), `PickingServiceTest` (pick đơn, lô bị chặn → chèn relocation, thiếu hàng → shortfall).
- **Lớp realtime (STOMP/WebSocket)** — khi ghi movement, backend đẩy `PlacementDelta` (UPSERT/REMOVE theo `lotId`) tới `/topic/lane/{laneId}`; 3D scene cập nhật tức thì, không cần reload. Phát sự kiện từ `MovementService` qua `ApplicationEventPublisher` → `PlacementBroadcaster` (`@TransactionalEventListener(AFTER_COMMIT)`, không phát khi rollback). Endpoint STOMP `/ws`, simple broker `/topic`. RELOCATE khác lane: UPSERT tới lane đích + REMOVE tới lane gốc. Frontend dùng `@stomp/stompjs`, subscribe các lane đã tải, gộp delta theo `lotId`. Package `com.stockpile.realtime`; ADR-0005.
- Test (Testcontainers + STOMP client thật, `RANDOM_PORT`): `PlacementBroadcastTest` — putaway→UPSERT, pick→REMOVE, relocate khác lane→UPSERT+REMOVE.

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
