# ADR-0006: Picking Engine — đơn hàng → pick-list (FEFO/FIFO + tự chèn relocation)

## Trạng thái
Accepted — 2026-07-02

## Bối cảnh
Package `com.stockpile.picking` tồn tại nhưng rỗng; roadmap §8.4 chỉ ghi một dòng:
"zone/wave picking để tránh chồng lối đi; FIFO/FEFO cho hàng có hạn dùng". Cần chốt
**phạm vi** engine cho v1 và cách nó khớp với hai engine đã có (Putaway/SLAP,
Relocation/CRP) mà không phá invariant "ledger là nguồn sự thật" (ADR-0003). Có sẵn:
`MovementType.PICK`, `Sku.handling` (FIFO/FEFO), `Lot.expiry` + `predictedRetrievalAt`,
`BlockingGraph`, `RelocationService`. Chưa có entity đơn hàng.

## Quyết định
- **Phạm vi: Order + pick-list.** Thêm entity `PickOrder` + `OrderLine` (Flyway `V2`;
  bảng `pick_order` vì `order` là từ khóa SQL). `qty` đếm **kiện/thùng/pallet** — đơn vị
  kho theo dõi, không phải số lượng sản phẩm. Có CRUD đơn (`/api/orders`).
- **Engine là proposal-only** như Putaway/Relocation: `PickingService.plan(orderId)` trả
  `PickPlan` (không ghi ledger). Thực thi (ghi movement) là hành động riêng người dùng xác nhận.
- **Chọn lô — chính sách dẫn, ít-bị-chặn phá hòa:** tách phần quyết định thành lõi thuần
  `PickPlanner` (không DB, test được như `BlockingGraph`/`PutawayScorer`). Thứ tự:
  - **FEFO** → lô `expiry` sớm nhất trước; **FIFO** → lô cũ nhất (`predictedRetrievalAt`) trước;
  - **phá hòa** khi cùng hạn/tuổi: lô **ít bị chặn nhất** (số lô phải dời ít nhất).
  Đây là dung hòa: người vận hành muốn "lấy lô dễ nhất", nhưng để least-blocked *lấn át*
  chính sách sẽ khiến hàng gần hết hạn kẹt trong kho (vi phạm §8.4). Nên áp least-blocked
  **bên trong** nhóm cùng hạn/tuổi — dễ khi được phép, không bao giờ bỏ sót hàng gần hết hạn.
- **Tự chèn relocation cho lô bị chặn:** với mỗi lô được chọn, nếu bị chặn thì gọi
  `RelocationService.plan(lotId)` (engine CRP) và **chèn các bước dời trước** bước PICK →
  pick-list "chạy được ngay". Engine **tái dùng** CRP, không giải lại bài toán chặn.
- **Hợp đồng output:** `PickPlan { orderId, List<PickLineResult>, List<PickStep> }`;
  `PickStep { kind (RELOCATE|PICK), lotId, fromBinId, toBinId }`; `PickLineResult` báo
  `requested/fulfilled/shortfall` khi thiếu hàng. Endpoint `GET /api/pick-plan?orderId=`.

## Hệ quả
Tích cực:
- Engine thứ ba **kết hợp** hai engine sẵn có (BlockingGraph + RelocationService) thay vì
  trùng lặp — pick-list là chuỗi thao tác hoàn chỉnh, đúng giá trị lõi "lấy 1 lô kéo theo
  bao việc phụ".
- Lõi chọn lô thuần (`PickPlanner`) test bằng mili-giây không cần Docker; I/O ở service.
- Ledger vẫn là nguồn sự thật — picking chỉ đọc, đề xuất (ADR-0003 nguyên vẹn).

Tiêu cực / đánh đổi:
- v1 **chưa** tối ưu tuyến đi (route/TSP trong aisle) hay wave/zone picking đồng thời —
  chỉ chọn lô + giải chặn. Route optimization là slice sau (cần tọa độ lối đi/điểm vào).
- Lô được chọn theo từng dòng độc lập; chưa gộp tuyến giữa các dòng cùng lane.
- Frontend (animation pick-list trên 3D) là slice sau — bản này backend-only.
- Relocation được tính **độc lập** cho từng lô bị chặn; nếu nhiều lô trong một plan dùng
  chung ô đích tạm, kế hoạch có thể chồng lấn — chấp nhận ở quy mô v1, tinh chỉnh sau.
