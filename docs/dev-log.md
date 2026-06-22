# Dev Log — Stockpile-3D

> Nhật ký vấn đề gặp phải + nguyên nhân + cách giải, viết cho chính mình (theo [03-documentation.md](./03-documentation.md) §5). Mới nhất ở trên.

## 2026-06-22 — Lô xếp khít không bị coi là chặn (CRP)

- **Vấn đề:** chạy thử `GET /api/relocation-plan` với 2 lô xếp chồng **khít** (lô dưới z[0,2], lô trên z[2,4]) → plan trả **rỗng**, tức không phát hiện lô trên chặn lô dưới. Sai với thực tế block-stacking.
- **Nguyên nhân:** luật on-top dùng so sánh **chặt** `B.zMin > A.zMax`. Khi xếp khít, `2 > 2` = false → bỏ sót. (Pose placement = góc bin nên các lô luôn khít, không có khe hở → CRP gần như vô dụng.)
- **Đã sửa:** đổi sang `B.zMin >= A.zMax`. Lô khít giờ bị coi là chặn (đúng vật lý). Quan trọng: edge case "2 lô **cùng tầng**" *vẫn* không chặn, vì khi cùng z thì z-range trùng nhau (`0 >= 1` = false) — và phép kiểm **overlap (x,y)** mới là cái phân biệt "chồng lên" vs "cạnh nhau", không phải dấu `>`/`>=`.
- **Học được:** dấu so sánh biên (`>` vs `>=`) phải xét cùng với *cách dữ liệu được sinh ra* (pose=góc bin → luôn khít). Test logic thuần (`BlockingGraphTest`) bắt được ngay sau khi sửa; nhưng chỉ chạy thử API thật mới lộ ra bug — test ban đầu vô tình "khóa" hành vi sai.
- Cập nhật: `BlockingGraph.blocksOnTop`, test #1/#5, `docs/algorithm-spec.md`. Liên quan entry 2026-06-25 bên dưới (cùng chỗ code, hiểu rõ hơn).

## 2026-06-25 — Blocking graph tính sai khi 2 lô cùng tầng

- **Vấn đề:** blocking graph tính sai khi 2 lô cùng `z_min` (cùng tầng) — coi nhầm lô này nằm trên lô kia.
- **Nguyên nhân:** dùng `>=` thay vì `>` khi check "nằm trên" (so sai cặp biên).
- **Đã sửa:** phân biệt rõ "cùng tầng" vs "tầng trên".
- **Học được:** cần test case cho edge case "2 object cùng toạ độ biên" ngay từ đầu.

> *(Ghi chú: entry 2026-06-25 là bản ghi sớm; entry 2026-06-22 ở trên hoàn thiện cách hiểu — luật cuối cùng là `zMin >= zMax` + overlap (x,y), xem [algorithm-spec.md](./algorithm-spec.md) §3.)*
