# ADR-0009: Multi-warehouse — nhiều kho độc lập trong một CSDL, blocking vẫn cục bộ theo lane

## Trạng thái

Accepted — 2026-07-04

## Bối cảnh

Roadmap giai đoạn 4 (docs/01 §9) còn đúng một mục: **multi-warehouse** — "sẵn sàng nhân rộng". Quyết định khóa ở docs/01 §6 ("không dùng PostGIS/R-tree toàn cục cho v1, phân vùng theo `(zone, aisle, rack)`") ghi rõ *"để dành nâng cấp index không gian cho giai đoạn multi-warehouse"* — tức ADR này nợ một lần xem xét lại quyết định đó.

Hiện trạng single-warehouse ăn sâu vào code:

- `location` không có khái niệm kho; mã ô `zone-aisle-rack-level-bin` là UNIQUE **toàn cục** — hai kho không thể cùng có ô `01-01-01-01-01`.
- `findEmpty()` (fallback của CRP khi lane đầy, và nguồn ứng viên SLAP) quét **mọi** location — một lệnh relocation có thể "chuyển lô sang kho khác" mà không ai nhận ra.
- Dock gắn cứng ở gốc tọa độ `(0,0,0)` (`PutawayScorer.distToDock`, docs/algorithm-spec §SLAP).
- Topic STOMP `/topic/lane/{laneId}` không phân biệt kho — hai kho trùng `laneId` sẽ nghe delta của nhau.
- Generator từ chối chạy khi DB đã có bất kỳ location nào — không thể tạo kho thứ hai.
- Heatmap / reports / what-if / danh sách placement đều `findAll()` toàn cục.

Phạm vi nghiệp vụ đã chốt với người dùng: v1 là **nhiều kho vật lý độc lập trong một CSDL** — người dùng chọn kho đang làm việc, mọi engine/báo cáo/quét hoạt động trong kho đó. **Không** có chuyển hàng giữa kho (transfer) và **không** phải multi-tenant (không có lớp xác thực/tổ chức).

## Quyết định

- **Thực thể `warehouse` mới** (`id`, `code` UNIQUE, `name`, `created_at`); `location.warehouse_id NOT NULL`. Khóa duy nhất của mã ô trở thành `(warehouse_id, zone, aisle, rack, level, bin)` — mã ô 5 đoạn chỉ cần duy nhất **trong một kho**.
- **Xem lại quyết định spatial index (docs/01 §6): giữ nguyên, thu hẹp phạm vi — không supersede.** Một lane luôn nằm trọn trong một kho, nên blocking cục bộ theo lane vẫn đúng nguyên vẹn; khóa phân vùng chỉ đổi từ `lane_id` thành `(warehouse_id, lane_id)`. Vẫn **không** PostGIS/R-tree: multi-warehouse nhân số kho lên, nhưng mỗi truy vấn blocking vẫn chỉ đụng vài chục lô trong một lane. Nâng cấp index không gian chỉ cần bàn lại nếu một kho đơn lẻ vượt xa quy mô mục tiêu (docs/01 §7).
- **Tọa độ cục bộ theo kho.** Mỗi kho có hệ tọa độ riêng với gốc `(0,0,0)` của chính nó; dock quy ước ở gốc như cũ. `PutawayScorer`/`BlockingGraph`/`RelocationPlanner` (các lõi thuần) **không đổi một dòng nào** — chỉ tầng I/O lọc dữ liệu theo kho trước khi đưa vào.
- **Mọi truy vấn đọc nhận `warehouseId` bắt buộc** (`/api/locations`, `/api/placements`, `/api/heatmap`, `/api/scan`, `/api/lots/locate`, `/api/locations/locate`, `/api/reports/*`, `/api/whatif/layout`, `/api/putaway-suggestion`). Ngoại lệ — nơi kho suy ra được từ dữ liệu: `/api/relocation-plan?lotId=` (kho của placement), `/api/pick-plan?orderId=` (kho của đơn).
- **`movement.warehouse_id NOT NULL` — ledger ghi rõ sự kiện xảy ra ở kho nào.** Suy từ bin (`to_bin` trước, `from_bin` sau); khi cả hai bin đều null (INBOUND về staging) client **phải** khai `warehouseId`. Đi kèm luật thi hành "chưa có transfer": movement có `from_bin` và `to_bin` thuộc hai kho khác nhau bị **từ chối** ở tầng ghi sổ.
- **`pick_order.warehouse_id NOT NULL`** — một đơn được soạn từ đúng một kho; picking engine chỉ xét ứng viên trong kho của đơn.
- **Barcode giữ nguyên ADR-0007** (mã suy diễn, không cột barcode): mã ô 5 đoạn không đổi, `GET /api/scan` resolve mã ô **trong kho đang chọn**; `LOT-{id}` vẫn toàn cục vì id lot là toàn cục.
- **Topic STOMP thêm tầng kho:** `/topic/warehouse/{warehouseId}/lane/{laneId}` (thay `/topic/lane/{laneId}`). Client đăng ký theo kho đang xem; các lane trùng tên giữa hai kho không còn nghe lẫn nhau.
- **Generator theo kho:** `POST /api/warehouses/{id}/generate` (thay `POST /api/warehouse/generate`); guard "kho đã có location thì từ chối" tính **theo kho đích**, nên tạo kho thứ hai/ba là thao tác bình thường. `buildGrid` thuần giữ nguyên cho what-if.
- **Migration V3** backfill toàn bộ dữ liệu hiện có vào kho mặc định `MAIN` (chỉ tạo khi DB có dữ liệu), rồi siết `NOT NULL` — DB trống migrate sạch không sinh kho thừa.

## Hệ quả

Tích cực:

- Đúng lời hứa roadmap "sẵn sàng nhân rộng": thêm kho là một lệnh `POST /api/warehouses` + generate, không đụng schema.
- Ba lõi thuật toán thuần không đổi — toàn bộ giá trị đã kiểm chứng của CRP/SLAP/BlockingGraph giữ nguyên, kể cả bộ test thuần.
- Nhãn mã ô 5 đoạn đã in không phải in lại (ADR-0007 nguyên vẹn); mã ô trùng nhau giữa hai kho là hợp lệ.
- Luật "không transfer" nằm ở tầng ghi sổ (từ chối movement xuyên kho) chứ không chỉ ở UI — ledger không thể chứa dữ liệu vô nghĩa.
- Ledger có `warehouse_id` nên báo cáo theo kho không cần join suy diễn, và INBOUND staging (không bin) vẫn quy được về kho.

Tiêu cực / đánh đổi:

- **Client phải giữ ngữ cảnh kho.** Mọi lời gọi đọc cần `warehouseId`; quét mã ô mà chưa chọn kho là không resolve được. Đây là hệ quả trực tiếp của việc giữ mã ô 5 đoạn.
- `movement.warehouse_id` là denormalization (suy được từ bin trong đa số trường hợp) — đổi lấy truy vấn báo cáo đơn giản và ngữ nghĩa rõ cho movement không bin.
- Đổi khóa UNIQUE của `location` và đổi đường dẫn generate + topic STOMP là **breaking change** với client cũ — chấp nhận vì frontend nằm cùng repo, cập nhật cùng một release.
- Transfer giữa kho khi cần sẽ là ADR riêng (loại movement mới hoặc cặp OUTBOUND/INBOUND có liên kết) — v1 cố tình không làm.
