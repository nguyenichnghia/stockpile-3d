# ADR-0010: Cross-warehouse transfer — chuyển lô giữa hai kho

## Trạng thái

Accepted — 2026-07-06

> ADR này nới đúng **một** luật của [ADR-0009](0009-multi-warehouse.md): luật "từ
> chối movement xuyên kho" ở §Quyết định (bút toán có `from_bin`/`to_bin` khác
> kho bị chặn). Mọi phần khác của ADR-0009 giữ nguyên hiệu lực — và như sẽ thấy
> ở §Quyết định, phương án được chọn thậm chí **không** cần lật luật đó: nó diễn
> đạt transfer bằng hai bút toán **cùng-kho** nên `validateWarehouse` không đổi.

## Bối cảnh

ADR-0009 chốt multi-warehouse là **nhiều kho độc lập**: mỗi kho một hệ tọa độ
riêng (dock ở gốc `(0,0,0)`), mã ô 5 đoạn duy nhất trong một kho, và
`MovementService.validateWarehouse` **từ chối** mọi movement có `from_bin` và
`to_bin` thuộc hai kho khác nhau — "một bút toán, một kho". Đó là quyết định
đúng cho v1, nhưng ADR-0009 §Hệ quả đã ghi sẵn lối thoát: *"Transfer giữa kho
khi cần sẽ là ADR riêng (loại movement mới hoặc cặp OUTBOUND/INBOUND có liên
kết)"*. ADR này là ADR đó.

**Nhu cầu:** chuyển một lô đang ở kho A sang một ô ở kho B (dời kho, cân bằng
tồn, gom hàng gần khách). Trong thực tế đây là một chuyến vận chuyển vật lý mất
thời gian — lô rời A, đi đường, rồi tới B — chứ không phải một bước tức thời như
RELOCATE trong cùng lane.

**Ràng buộc bất biến phải tôn trọng (không được lật nếu không có ADR khác):**

- **Ledger append-only là nguồn sự thật; `placement` là projection.** Bất kỳ mô
  hình nào cũng phải diễn đạt transfer bằng (các) bút toán ledger, để
  `rebuildAll()` phát lại ra đúng trạng thái. Không vá thẳng `placement`.
- **Blocking cục bộ theo `(warehouse_id, lane_id)`.** Transfer không được tạo
  quan hệ blocking xuyên kho — lô chỉ chặn nhau trong cùng một lane của cùng một
  kho. Ba lõi thuần (`BlockingGraph`/`PutawayScorer`/`RelocationPlanner`) không
  được đổi.
- **Tọa độ cục bộ theo kho.** Lô ở A và ô đích ở B không cùng hệ quy chiếu;
  `distToDock` của mỗi kho tính theo gốc của chính nó. Projection lấy pose từ
  tọa độ ô đích (đã đúng sẵn — `upsert` copy `bin.x/y/z`), nên chuyển hệ quy
  chiếu không phải vấn đề với placement, nhưng đừng giả định A và B liên thông
  về không gian.
- **`movement.warehouse_id NOT NULL`, suy từ bin.** Nếu giữ "một bút toán một
  kho", một bút toán transfer đơn lẻ sẽ mơ hồ thuộc kho nào.

**Câu hỏi cần chốt:** (1) transfer là **một** bút toán xuyên kho hay **hai** bút
toán ghép? (2) hàng đang đi đường (in-transit) biểu diễn thế nào? (3) mô phỏng
CRP/SLAP có tham gia không, hay transfer chỉ là chọn ô đích thủ công?

### Các phương án đang cân nhắc

**Phương án A — Một loại movement mới `TRANSFER` (một bút toán xuyên kho).**
Thêm `MovementType.TRANSFER`; nới `validateWarehouse` để cho phép đúng loại này
có hai bin khác kho. Projection: `from_bin` (kho A) → `to_bin` (kho B) trong một
bước, y như RELOCATE nhưng xuyên kho. `movement.warehouse_id` phải chọn quy ước
(kho đích B, để báo cáo "hàng vào B" khớp nơi lô kết thúc).
- *Được:* một bút toán, mô hình gọn; projection gần như tái dùng nhánh RELOCATE.
- *Mất:* phá thẳng bất biến "một bút toán một kho" — `warehouse_id` của bút toán
  không còn suy nhất quán từ bin (from ≠ to về kho). Không biểu diễn được
  in-transit (lô "nhảy" tức thời từ A sang B). Báo cáo throughput của A không
  thấy hàng rời đi trừ khi đếm cả TRANSFER as outbound-like.

**Phương án B — Cặp `OUTBOUND` (A) + `INBOUND` (B) có liên kết `transfer_id`.**
Transfer = hai bút toán độc lập, mỗi bút toán thuộc đúng một kho (giữ nguyên bất
biến "một bút toán một kho"), nối bằng một cột/bảng liên kết. OUTBOUND ở A xóa
placement (lô rời A → in-transit = không có placement, đúng như staging); INBOUND
ở B đặt lô vào ô đích khi hàng tới.
- *Được:* **không lật** bất biến nào của ADR-0009 — `validateWarehouse` giữ
  nguyên, mỗi bút toán vẫn một kho, `warehouse_id` vẫn suy từ bin. In-transit là
  trạng thái tự nhiên (lô tồn tại, không placement) giữa hai bút toán. Báo cáo
  hai kho khớp thực tế: A thấy outbound, B thấy inbound. Tái dùng đúng ngữ nghĩa
  OUTBOUND/INBOUND sẵn có.
- *Mất:* một transfer là hai bản ghi + một liên kết → cần bảng/cột `transfer`
  (id, from_wh, to_wh, trạng thái, hai movement id) và migration. Lô in-transit
  "biến mất" khỏi cả hai scene 3D cho tới khi INBOUND — cần thể hiện ở UI (danh
  sách "đang chuyển"). Hai bước phải nhất quán (tạo OUTBOUND nhưng chưa INBOUND
  là trạng thái hợp lệ, không phải lỗi) — cần logic vòng đời.

**Phương án C — Chưa làm transfer thật, chỉ "dời sổ" (administrative move).**
Không có chuyến vật lý; chỉ đính chính lô thuộc kho nào (ví dụ nhập sai kho lúc
đầu). Một bút toán chỉnh sửa, không mô phỏng vận chuyển.
- *Được:* nhỏ nhất; đủ nếu nhu cầu thật chỉ là sửa lỗi gán kho.
- *Mất:* không phải transfer vật lý — không giải quyết nhu cầu dời hàng thật. Có
  thể gây hiểu nhầm là đã hỗ trợ transfer.

## Quyết định

Chọn **Phương án B — transfer là một cặp `OUTBOUND` (kho A) + `INBOUND` (kho B)
liên kết**, vì nó lấp nhu cầu transfer vật lý mà **không lật** bất biến nào của
ADR-0009: mỗi bút toán vẫn thuộc đúng một kho (`validateWarehouse` không đổi),
ledger vẫn là nguồn sự thật, và "hàng đang đi đường" là một trạng thái tự nhiên
(lô tồn tại, không có placement) giữa hai bút toán — đúng như INBOUND-về-staging
đã có sẵn.

- **Thực thể `transfer` mới (Flyway `V6`):** `id`, `lot_id`, `from_warehouse_id`,
  `to_warehouse_id`, `status`, `outbound_movement_id`, `inbound_movement_id`
  (nullable tới khi tới nơi), `created_at`, `completed_at`. Đây **không** phải
  nguồn sự thật về vị trí — nó chỉ *liên kết* hai bút toán ledger và mang trạng
  thái chuyến đi. Vị trí lô vẫn hoàn toàn suy từ ledger như mọi khi.
- **Vòng đời hai trạng thái:** `IN_TRANSIT` (đã ghi OUTBOUND ở A, lô rời kho A →
  không còn placement) → `COMPLETED` (đã ghi INBOUND vào ô đích ở B). **v1 không
  có `CANCELLED`** — giữ phạm vi nhỏ; hủy/hoàn hàng là ADR sau nếu cần. Một
  transfer `IN_TRANSIT` chưa có INBOUND là **trạng thái hợp lệ**, không phải lỗi.
- **Hai API, hai bước rõ ràng** (khớp nguyên tắc ADR-0009 "engine đề xuất, người
  dùng xác nhận từng bước", và ledger append-only):
  - `POST /api/transfers` `{lotId, toWarehouseId}` — mở transfer: ghi một
    `OUTBOUND` ở kho A (kho hiện tại của lô, suy từ placement; `from_bin` = ô
    đang chiếm, không `to_bin`), tạo hàng `transfer` `IN_TRANSIT`.
  - `POST /api/transfers/{id}/receive` `{toBinId}` — nhận hàng: ghi một
    `INBOUND` ở kho B (`warehouseId` = B, `to_bin` = ô đích do người dùng chọn),
    cập nhật transfer `COMPLETED` + `inbound_movement_id`.
- **Ô đích ở kho B chọn thủ công** (client truyền `toBinId` khi nhận). Dùng SLAP
  để **gợi ý** ô đích ở B là slice sau — tái dùng `PutawayService` với ứng viên
  lọc theo kho B; ADR này không làm để giữ nhỏ.
- **`warehouse_id` của mỗi bút toán vẫn suy từ bin** như ADR-0009: OUTBOUND lấy
  từ `from_bin` (kho A), INBOUND lấy từ `to_bin` (kho B). Không bút toán nào có
  hai bin khác kho, nên `MovementService.validateWarehouse` **giữ nguyên** — luật
  "từ chối movement xuyên kho" vẫn đúng và vẫn bảo vệ chống transfer sai (một
  bút toán RELOCATE xuyên kho vẫn bị chặn; chỉ đường transfer chính thức mới nối
  được hai kho, và nó nối bằng hai bút toán hợp lệ).
- **Blocking / tọa độ / spatial-index:** không đụng. Lô in-transit không có
  placement nên không tham gia blocking ở bất kỳ kho nào; khi INBOUND vào B nó
  gia nhập lane của B như một putaway thường. Ba lõi thuần không đổi một dòng.

## Hệ quả

Tích cực:

- **Không lật bất biến nào của ADR-0009.** `validateWarehouse` nguyên vẹn, mỗi
  bút toán một kho, ledger vẫn nguồn sự thật, `rebuildAll()` phát lại đúng (mỗi
  bút toán là OUTBOUND/INBOUND đã hiểu sẵn). Rủi ro thấp nhất trong ba phương án.
- **Báo cáo hai kho khớp thực tế:** kho A thấy một OUTBOUND (hàng rời đi), kho B
  thấy một INBOUND (hàng tới) — không cần loại throughput mới, `movementsDaily`
  và "movement hôm nay" (ADR-0009 §reports, timezone V5) cộng đúng cho từng kho.
- **In-transit biểu diễn tự nhiên:** lô tồn tại nhưng không placement giữa hai
  bước — tái dùng đúng trạng thái INBOUND-staging, không cần khái niệm mới trong
  projection.

Tiêu cực / đánh đổi:

- **Một transfer = hai bút toán + một hàng liên kết + migration V6.** Nhiều
  "phần chuyển động" hơn phương án A một-bút-toán; đổi lấy việc không phá bất
  biến.
- **Lô in-transit "biến mất" khỏi cả hai scene 3D** cho tới khi nhận — cần thể
  hiện ở UI (danh sách "đang chuyển" trên `/reports` hoặc trang riêng) để người
  dùng không tưởng mất hàng. Frontend v1 sẽ có tối thiểu: danh sách transfer
  `IN_TRANSIT` + nút "Nhận" chọn ô đích.
- **Vòng đời hai bước phải nhất quán:** mở transfer nhưng chưa nhận là hợp lệ
  lâu dài; cần chặn nhận hai lần, chặn nhận vào kho sai (ô đích phải thuộc
  `to_warehouse_id`), chặn mở transfer cho lô không có placement (đang staging /
  đã đi transfer khác).
- **Chưa có hủy/hoàn.** Nếu chuyến hỏng giữa đường, v1 không rollback được bằng
  API — phải nhận rồi transfer ngược, hoặc chờ ADR bổ sung `CANCELLED`.
- **`transfer_id` trên movement là liên kết một chiều (bảng `transfer` trỏ tới
  movement, không ngược lại)** — giữ `movement` không đổi schema (append-only,
  không thêm cột), đổi lấy việc muốn biết "movement này thuộc transfer nào" phải
  join từ phía `transfer`.
