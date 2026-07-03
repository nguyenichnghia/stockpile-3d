# ADR-0007: Barcode scan touchpoints — mã suy diễn, xác nhận ở client, ledger giữ scanRef

## Trạng thái
Accepted — 2026-07-03

## Bối cảnh
Rủi ro số 1 của dự án là **dữ liệu sai** (docs/01 §10): tọa độ/lô lệch thực tế thì CRP/SLAP
vô nghĩa. §8.6 đặt mục tiêu "scan bắt buộc mọi điểm chạm vật lý". Schema V1 đã chừa sẵn cột
`movement.scan_ref` nhưng chưa ai ghi; lô và ô chưa có khái niệm mã vạch. Pick-list 3D
(PR #21) đã có bước xác nhận từng bước — điểm chạm vật lý đầu tiên cần scan. Câu hỏi cần
chốt: (1) mã vạch của lô/ô là gì, (2) ai kiểm tra mã đúng/sai, (3) có bắt buộc scan không.

## Quyết định
- **Mã vạch v1 là định danh suy diễn, không thêm cột schema:** lô = `LOT-{id}` (id ledger
  cấp, in tem khi nhập kho); ô = chính mã ô `zone-aisle-rack-level-bin` (dán tại vị trí).
  Không thêm cột `barcode` — tránh migration + màn quản trị tem cho giá trị v1 bằng không.
  Khi cần SSCC/GS1 thật (in tem từ hệ thống khác), viết ADR mới thay thế quy ước này.
- **Backend chỉ phân giải, không phán xử:** `GET /api/scan?code=` (package
  `com.stockpile.scan`, read-only) trả về mã đó là gì — `LOT` (kèm vị trí hiện tại) / `BIN`
  (kèm lô đang chiếm) / `UNKNOWN`. Đây là entry point chung cho mọi touchpoint sau này
  (inbound, cycle count), không riêng pick.
- **Kiểm tra khớp ở client, tại điểm chạm:** khi xác nhận bước pick-list, frontend so mã
  quét với lô của bước (`LOT-{lotId}`) — khớp mới cho ghi movement. Backend **không** từ
  chối movement thiếu scanRef: POST `/api/movements` giữ nguyên hợp đồng (không breaking
  change cho client cũ/test/tool).
- **Ledger ghi lại bằng chứng:** movement ghi `scanRef` = chuỗi quét thô khi xác nhận bằng
  scan; xác nhận thủ công (không máy quét) vẫn được phép nhưng `scanRef = null` — audit
  phân biệt được điểm chạm có/không có ground-truth.

## Hệ quả
Tích cực:
- Cột `scan_ref` nằm chờ từ V1 giờ có dữ liệu; audit trail phân biệt bước đã quét/chưa quét.
- Không migration, không breaking change; máy quét kiểu bàn phím (keyboard wedge) hoạt động
  ngay với input + Enter, không cần driver.
- `/api/scan` tái dùng được cho mọi touchpoint sau (inbound, putaway, cycle count).

Tiêu cực / đánh đổi:
- **Chưa "bắt buộc" đúng nghĩa §8.6:** backend vẫn nhận movement không scanRef — enforcing
  thật (từ chối 4xx, hoặc theo cấu hình từng kho) là slice sau, cần chính sách vận hành rõ
  trước khi khóa. v1 chọn *khuyến khích + audit* thay vì *ép buộc*.
- Kiểm tra khớp ở client là tin client; một client cố tình gửi scanRef giả vẫn qua. Chấp
  nhận ở v1 (client duy nhất là UI của dự án) — enforcing server-side đi cùng slice trên.
- Mã `LOT-{id}` lộ id nội bộ và đòi in tem theo id sau khi tạo lô; với kho in tem trước
  (pre-printed SSCC) quy ước này không đủ — đã ghi rõ lối thoát: ADR mới + cột barcode.
