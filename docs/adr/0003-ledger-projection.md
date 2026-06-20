# ADR-0003: Movement ledger append-only + placement là projection

## Trạng thái
Accepted — 2026-06-20

## Bối cảnh
Trạng thái kho (lô nào ở vị trí nào) cần vừa **truy vấn nhanh** (cho 3D, CRUD,
engine) vừa **đáng tin để audit/replay** (kiểm kê, dò sai lệch). Hai yêu cầu này
kéo về hai hướng lưu trữ khác nhau: một bảng trạng thái hiện tại (nhanh) vs. một
nhật ký mọi thay đổi (đáng tin). docs/01 §5 đã nêu nguyên tắc "ledger là nguồn sự
thật, placement là projection", nhưng chưa có cơ chế hiện thực.

## Quyết định
- **`movement` là ledger append-only**: mọi thay đổi vật lý (INBOUND, PUTAWAY,
  RELOCATE, PICK, OUTBOUND) là một bút toán *chỉ thêm* — không update/không delete.
  Tầng service (`MovementService.record`) chỉ `save` movement, không sửa lịch sử.
- **`placement` là projection** dựng ra từ ledger, **không** phải nguồn dữ liệu
  độc lập. Một quy tắc chuyển trạng thái duy nhất (`PlacementProjectionService.apply`)
  dùng chung cho:
  - **incremental**: mỗi movement ghi xong → cập nhật placement ngay (real-time),
  - **rebuild**: `rebuildAll()` xóa placement và replay toàn bộ ledger theo `ts`.
- **Pose của placement (v1)** = tọa độ góc của bin đích. Pose/stacking chính xác
  để Phase 2 (blocking graph).
- Khi nghi ngờ sai lệch giữa hai nơi: **ledger thắng** (chạy `rebuildAll`).

## Hệ quả
Tích cực:
- Audit & replay được "miễn phí": lịch sử đầy đủ, dựng lại trạng thái bất kỳ.
- Một nguồn logic duy nhất cho projection → incremental và replay không thể lệch
  nhau (đã khẳng định bằng test `incrementalProjectionMatchesLedgerReplay`).
- Đặt đúng nền cho cycle count và đồng bộ realtime (Phase 3).

Tiêu cực / đánh đổi:
- Ghi đắt hơn: mỗi thay đổi vừa append ledger vừa cập nhật projection trong cùng
  transaction.
- Placement là dữ liệu dẫn xuất → phải kỷ luật: **không** sửa placement trực tiếp
  ngoài projection, nếu không sẽ lệch ledger.
- `rebuildAll` quét toàn bộ ledger; với ledger rất lớn sẽ cần rebuild theo phạm vi
  (vd theo lane) — để sau khi cần.
