# ADR-0001: Greedy heuristic cho Relocation Engine (CRP)

## Trạng thái
Accepted — 2026-06-21

## Bối cảnh
**CRP — Container/Block Relocation Problem** (bài toán di dời khối): cho một lô
mục tiêu cần lấy ra, tìm chuỗi di chuyển *tối thiểu* để giải phóng nó, sao cho
mỗi lô bị dời được đặt vào vị trí tạm hợp lệ không gây kẹt mới. Đây là bài toán
**NP-hard** đã biết trong literature (Caserta, Schwarze, Voß — survey container
rehandling; và chứng minh độ phức tạp của Block Relocation Problem) — nghĩa là
chưa có thuật toán giải *tối ưu* nhanh cho mọi trường hợp khi dữ liệu lớn.

Ràng buộc dự án:
- Quy mô mục tiêu: ≤ ~100 lô chồng chéo trong một lane (NFR §7).
- Yêu cầu phản hồi **tương tác** (< 500 ms) để người vận hành không phải chờ.
- Kết quả phải **giải thích được** cho người vận hành (họ làm theo từng bước).

## Quyết định
Dùng **heuristic greedy** (thuật toán tham lam — chọn bước tốt nhất tại mỗi
thời điểm, không tối ưu toàn cục), thao tác trên **đồ thị blocking cục bộ theo
lane** (không index không gian toàn cục — xem invariant docs/01 §6):

1. Tính tập lô đang chặn trực tiếp lô mục tiêu (on-top + in-front).
2. Trong các lô chặn, chọn lô ưu tiên dời: **dự đoán lấy ra muộn nhất**
   (`predictedRetrievalAt`; null coi như muộn), tie-break theo **z cao nhất**
   (dời từ trên xuống — vật lý hợp lý).
3. Đặt lô đó vào **vị trí tạm gần nhất không tạo blocking mới** (ưu tiên cùng
   lane, fallback toàn kho).
4. Lặp tới khi lô mục tiêu hết bị chặn.

Hiện thực ở `relocation/service/` (`BlockingGraph` thuần + `RelocationService`).
Engine chỉ **đề xuất** kế hoạch (`GET /api/relocation-plan`); việc thực thi
(ghi ledger) là hành động riêng do người dùng xác nhận — 3D không tự di chuyển.

**Định nghĩa blocking** (docs/01 §6): on-top dùng so sánh **`>` chặt** cho trục z
(`B.zMin > A.zMax`) để hai lô *cùng tầng* không bị coi là chồng nhau (edge case
Dev Log 2026-06-25); in-front xét theo hướng `access_face` của lane.

## Hệ quả

Tích cực:
- Độ phức tạp thấp — `O(n²)` trường hợp xấu với n ≤ ~100/lane → real-time được.
- **Giải thích được**: mỗi bước là một quyết định đơn giản, dễ trình bày trên 3D
  (so với branch-and-bound/beam search khó diễn giải).
- Đủ tốt cho phần lớn ca thực tế của kho vừa.
- Thuật toán lõi thuần (không phụ thuộc DB/Spring) → test nhanh, đáng tin.

Tiêu cực / đánh đổi (không giấu):
- **Không đảm bảo số bước di chuyển tối thiểu tuyệt đối** — greedy có thể dời
  nhiều hơn lời giải tối ưu trong một số cấu hình.
- Chất lượng lời giải phụ thuộc tiêu chí chọn blocker; có thể cần tinh chỉnh.
- Sẽ cần đánh giá lại (beam search / branch-and-bound, hoặc index không gian)
  nếu mở rộng sang kho rất lớn (> ~10k vị trí) hoặc lane sâu bất thường.
- Pose v1 = góc bin (chưa mô hình stacking pose chính xác) — đủ cho v1, sẽ tinh
  chỉnh khi cần độ chính xác hình học cao hơn.
