# Đặc tả thuật toán — Relocation (CRP) & Putaway (SLAP)

> Tài liệu đặc tả chi tiết cho 2 thuật toán lõi. Theo khung trong [03-documentation.md](./03-documentation.md) §6. Quyết định chọn greedy cho CRP ghi ở [ADR-0001](./adr/0001-greedy-crp-heuristic.md). Thuật ngữ giải thích trong [glossary.md](./glossary.md).
>
> **Phần A — CRP (Relocation Engine):** §1–8 dưới đây. **Phần B — SLAP (Putaway Engine):** §9.

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

---

# Phần B — SLAP (Putaway Engine)

## 9. SLAP — chấm điểm chọn vị trí cất

### 9.1. Bài toán
**SLAP — Storage Location Assignment Problem** (bài toán gán vị trí lưu trữ): chọn vị trí trống để cất một lô mới sao cho **tổng chi phí vận hành kỳ vọng nhỏ nhất**. NP-hard ở dạng tổng quát.

- **Input:** `lotId` của lô cần cất.
- **Output:** `PutawaySuggestion` = `recommendedBinId` (vị trí điểm thấp nhất) + danh sách `candidates` đã xếp hạng (binId, score). Chỉ đề xuất — không tự cất.

### 9.2. Cách giải v1 — chấm điểm greedy
Với mỗi vị trí trống khả thi `c` (đã lọc cứng: lô phải **vừa** bin):
```
score(c) = w1·distToDock(c)              # gần dock (gốc 0,0,0) thì rẻ
         + w2·blockingPenalty(c, lô)     # phạt nếu đặt vào c sẽ chặn / bị chặn (dùng BlockingGraph)
         + w3·retrievalMisalignment(c,lô)# FEFO: lô có expiry nên ở z thấp (dễ lấy)
         + w4·fitPenalty(c, lô)          # phạt thể tích thừa (bin to hơn lô)
```
Chọn `c` có `score` **nhỏ nhất**. Trọng số `w1..w4` cấu hình qua `app.putaway.*` trong `application.yml`.

- **distToDock:** khoảng cách Euclid từ `(x,y,z)` của bin tới gốc tọa độ (dock giả định ở 0,0,0).
- **retrievalMisalignment (v1):** `z * urgency`, với `urgency = 2` nếu lô có `expiry` (nhạy FEFO), ngược lại `1`. → lô hết hạn sớm bị phạt mạnh hơn khi đặt cao → đẩy xuống thấp.
- **fitPenalty:** `max(0, thể_tích_bin − thể_tích_lô)` → ưu tiên bin vừa khít, đỡ phí chỗ.
- **Lọc cứng (fit):** `lô.w ≤ bin.w && lô.d ≤ bin.d && lô.h ≤ bin.h`; không vừa thì loại khỏi ứng viên.

### 9.3. Độ phức tạp
`O(F·k)` — `F` = số vị trí trống khả thi (sau lọc), mỗi đánh giá `O(k)` với `k` = số lô trong lane (cho blockingPenalty). Thực tế nhỏ. Tuyến tính → real-time.

### 9.4. Trade-off
Greedy + tuyến tính: **giải thích được** (mỗi điểm là tổng các chi phí rõ ràng), nhanh, cấu hình được theo kho. Không tối ưu toàn cục (vd không xét tương tác giữa nhiều lô cất cùng lúc). Đủ cho v1; có thể nâng cấp gán theo lô hàng loạt sau.

### 9.5. Test case
`PutawayServiceTest` (Testcontainers): (1) chọn bin gần dock + thấp nhất; (2) bỏ qua bin nhỏ hơn lô; (3) không bin nào vừa → `recommendedBinId = null`.

### 9.6. API
`GET /api/putaway-suggestion?lotId={id}` → `PutawaySuggestion`. `404` nếu lô không tồn tại. Chỉ đọc.
