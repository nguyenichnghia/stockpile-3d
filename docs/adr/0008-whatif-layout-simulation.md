# ADR-0008: What-if layout — mô phỏng thuần trong bộ nhớ, ghép các lõi sẵn có

## Trạng thái
Accepted — 2026-07-03

## Bối cảnh
Roadmap giai đoạn 4 (§8.5) hứa "mô phỏng what-if cho thay đổi layout" — câu hỏi cấp quản lý:
*nếu kho có layout khác, tồn kho hiện tại sẽ "khỏe" hơn không?* Đã có sẵn ba lõi thuần tách
khỏi DB: bộ dựng lưới (generator), `PutawayScorer` (SLAP) và `BlockingGraph`. Cần chốt phạm vi
v1: mô phỏng cái gì, chạy ở đâu, và tuyệt đối không đụng dữ liệu thật.

## Quyết định
- **Phạm vi v1: so sánh layout lưới.** `POST /api/whatif/layout` nhận `WarehouseGridSpec`
  (đúng DTO của generator), dựng lưới **trong bộ nhớ** (`WarehouseGeneratorService.buildGrid`
  được tách thuần, id âm tổng hợp, không save), xếp lại **toàn bộ lô đang đặt** vào lưới đó
  bằng chính `PutawayScorer` + trọng số `app.putaway.*`, rồi đo hai layout bằng chính
  `BlockingGraph`. Trả `WhatIfResult { current, simulated }` với các chỉ số: số ô, lô xếp
  được / không còn chỗ, lô bị chặn, mức lấp đầy, khoảng cách trung bình tới dock.
- **Thuần và không side effect:** transaction read-only, không ledger, không event, không
  đụng bảng `location`. POST chỉ vì spec đi trong body.
- **Thứ tự xếp mô phỏng là giả định có chủ đích:** expiry sớm trước (null cuối, id phá hòa) —
  quyết định luôn, FEFO-friendly, thay cho thứ tự nhập kho thật vốn không thể biết trước.
- **Chặn quy mô:** tối đa 100 000 ô mô phỏng; O(lô × ô) mỗi lần chạy (bằng một lời gọi
  putaway-suggestion cho từng lô).

## Hệ quả
Tích cực:
- Không viết thuật toán mới — what-if là **phép ghép** ba lõi thuần đã test kỹ, nên kết quả
  mô phỏng nhất quán tuyệt đối với heatmap/reports/scene (cùng luật blocking, cùng scorer).
- An toàn tự nhiên: không thể làm bẩn dữ liệu vì không có đường ghi nào.

Tiêu cực / đánh đổi:
- Kết quả **nhạy với thứ tự xếp giả định**; kho thật nhập hàng theo đợt có thể ra khác.
  Đủ cho so sánh tương đối giữa hai layout (mục đích của tính năng), không phải dự báo tuyệt đối.
- O(lô × ô) chưa tối ưu cho 20k lô × 50k ô; khi cần sẽ index ứng viên theo lane/kích thước.
- Chưa mô phỏng thay đổi **chính sách** (trọng số SLAP khác, accessFace khác cho từng zone)
  hay chi phí di dời từ layout cũ sang mới — các lát cắt sau.
