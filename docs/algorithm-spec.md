# Đặc tả thuật toán — Relocation Engine (CRP)

> Tài liệu đặc tả chi tiết cho thuật toán lõi. Theo khung trong [03-documentation.md](./03-documentation.md) §6. Quyết định chọn greedy ghi ở [ADR-0001](./adr/0001-greedy-crp-heuristic.md). Thuật ngữ giải thích trong [glossary.md](./glossary.md).

## 1. Bài toán (input / output)

**CRP — Container/Block Relocation Problem** (bài toán di dời khối): cho một **lô mục tiêu** (target lot) đang bị các lô khác chặn, tìm **chuỗi di chuyển tối thiểu** để có thể lấy nó ra, sao cho mỗi lô bị dời được đặt vào một vị trí tạm hợp lệ (không gây kẹt mới).

- **Input:** `lotId` của lô cần lấy. (Service tự tra trạng thái kho hiện tại từ `placement` + `location`.)
- **Output:** `RelocationPlan` = danh sách có thứ tự các bước `RelocationStep(lotId, fromBinId, toBinId)`. Danh sách rỗng nghĩa là lô đã lấy được ngay.
- **Ràng buộc quan trọng:** đây là **đề xuất**, engine **không** ghi vào ledger (sổ cái). Việc thực thi là hành động riêng do người dùng xác nhận (đúng invariant "3D trình bày, không ra quyết định").

## 2. Vì sao khó (NP-hard)

CRP (và biến thể Block Relocation Problem) là **NP-hard** (lớp bài toán khó — chưa có cách giải tối ưu nhanh cho mọi trường hợp khi dữ liệu lớn) — kết quả đã biết trong literature về container rehandling (xem Caserta, Schwarze, Voß — survey về container rehandling; và các chứng minh độ phức tạp của Block Relocation Problem). Số cách sắp xếp lại tăng tổ hợp theo số lô, nên tìm lời giải tối ưu tuyệt đối là không khả thi ở quy mô tương tác.

## 3. Quan hệ "blocking" (nền của thuật toán)

Lô **B chặn A** nếu để rút A bắt buộc phải dời B trước. Hai loại (docs/01 §6):

- **on-top** (chồng lên): `B.zMin >= A.zMax` **và** hình chiếu (x,y) của B giao A.
  - Dùng **`>=`**: lô xếp **khít** (đáy lô trên chạm đỉnh lô dưới, `B.zMin == A.zMax`) *vẫn* coi là chặn — vì lô trên đang đè lên lô dưới (đúng vật lý block-stacking).
  - Hai lô **cùng tầng** (cùng `z`) thì *không* chặn: z-range của chúng trùng nhau nên `zMin >= zMax` là sai (vd `0 >= 1` = false). Chính phép kiểm **overlap (x,y)** mới phân biệt "chồng lên" vs "cạnh nhau". (Edge case — Dev Log 2026-06-25 + 2026-06-22.)
- **in-front** (chặn trước): cùng lane (làn), cùng `access_face` (mặt lấy hàng), B nằm gần mặt lấy hàng hơn A theo trục rút, **và** overlap (giao nhau) trên 2 trục còn lại.
  - Trục rút theo mặt: NORTH/SOUTH → trục `y` (depth/chiều sâu); EAST/WEST → trục `x` (width/chiều rộng); TOP → quy về on-top.

Tập quan hệ này tạo **đồ thị blocking có hướng** (directed blocking graph). Code: `BlockingGraph.blocks(b, a)` (logic thuần, không DB).

## 4. Thuật toán chọn — Greedy heuristic

```
function plan(targetLotId):
    target ← placement của targetLotId            # 404 nếu không có
    lane   ← tất cả lô trong cùng lane (List<LotBox>)
    steps  ← []
    lặp tối đa |lane| lần:                          # chặn vòng vô hạn
        blockers ← các lô đang chặn trực tiếp target   # BlockingGraph.blockers
        nếu blockers rỗng: return RelocationPlan(target, steps)   # xong
        toMove ← chọn blocker ưu tiên dời (greedy)
        dest   ← vị trí tạm gần nhất, KHÔNG tạo blocking mới
        steps.append( (toMove, toMove.bin, dest) )
        mô phỏng dời toMove → dest (cập nhật lane in-memory)
    throw IllegalState  # không giải được trong giới hạn
```

**Greedy choice** (lựa chọn tham lam) — trong các lô đang chặn, ưu tiên dời lô:
1. có `predictedRetrievalAt` (dự đoán thời điểm lấy) **muộn nhất** — `null` coi như muộn nhất (dời lô ít cần tới trước);
2. tie-break (phá hòa): `zMin` **cao nhất** — dời từ trên xuống, hợp lý vật lý.

**Vị trí tạm (temp slot)** — vị trí trống (không có placement) ưu tiên **cùng lane**, sau đó toàn kho; phải **không tạo blocking mới** (kiểm bằng chính `BlockingGraph`). Hết chỗ → `IllegalStateException`.

## 5. Độ phức tạp (Big-O)

Với `n` = số lô trong lane (theo NFR ≤ ~100):
- Mỗi vòng: tính blockers `O(n)`, tìm temp slot duyệt ứng viên × kiểm blocking `O(n)` mỗi ứng viên.
- Số vòng tối đa `O(n)`.
- **Tổng: `O(n²)`** trong trường hợp xấu (thực tế nhỏ hơn nhờ lane bị giới hạn). Mục tiêu NFR: **< 500 ms** cho lane ≤ 100 lô — thừa sức đạt.

## 6. Trade-off + hướng cải thiện

- **Trade-off:** greedy **không** đảm bảo số bước tối thiểu tuyệt đối. Đổi lại: nhanh, real-time, và **giải thích được** cho người vận hành (so với branch-and-bound/beam search khó diễn giải). Đủ tốt cho phần lớn ca thực tế.
- **Cải thiện sau:** nếu mở rộng kho rất lớn hoặc cần tối ưu hơn — đánh giá beam search (tìm kiếm chùm) hoặc branch-and-bound (nhánh-cận), và tối ưu dựng đồ thị blocking xuống `O(n log n)` bằng index không gian theo lane.

## 7. Test case minh họa (input → output)

Hiện thực trong `BlockingGraphTest` (logic thuần) + `RelocationServiceTest` (qua DB Testcontainers):

| # | Tình huống | Kỳ vọng |
|---|---|---|
| 1 | Lô không bị chặn | `steps = []` |
| 2 | 1 lô chồng trực tiếp lên target (on-top) | plan 1 bước, dời đúng lô đó |
| 3 | Chuỗi 2–3 lô chồng | dời đúng thứ tự (trên xuống) |
| 4 | Lô chắn trước theo `access_face` (in-front) | lô chắn có trong plan |
| 5 | **2 lô cùng `z` (cùng tầng)** | KHÔNG coi là chặn nhau (z-range trùng → `zMin >= zMax` false) |
| 6 | **Lô xếp khít** (`B.zMin == A.zMax`, overlap x,y) | CÓ chặn — lô trên đè lô dưới (khẳng định luật `>=`) |
| 7 | Vị trí tạm được chọn | không tạo blocking mới cho lô vừa dời |

## 8. API

`GET /api/relocation-plan?lotId={id}` → `RelocationPlan` (JSON). `404` nếu lô/placement không tồn tại; `400` nếu không giải được (hết vị trí tạm). Chỉ đọc — không thay đổi trạng thái kho.
