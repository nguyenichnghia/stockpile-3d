# Stockpile-3D — Hệ thống Quản lý Kho 3D Thông minh
### Tài liệu tổng quan & thiết kế (Overview & Design)

---

## 1. Tóm tắt điều hành

WMS truyền thống xử lý tồn kho như các dòng trong bảng — số lượng, SKU, vị trí dạng text ("Kệ A-12-3"). Điều này tạo khoảng cách giữa **dữ liệu hệ thống** và **thực tế vật lý**: người vận hành không thấy lô nào đang chặn lô nào, không gian trống thực sự ở đâu, hay lấy 1 lô sẽ kéo theo bao nhiêu việc phụ.

Stockpile-3D biểu diễn kho dưới dạng **mô hình không gian 3 chiều thời gian thực**, kết hợp **engine tối ưu vận hành** (putaway, picking, relocation). 3D không phải trang trí — nó là lớp diễn giải trực quan cho một bài toán tối ưu tổ hợp đã được giải ở backend.

**Định vị:** không cạnh tranh trực diện WMS enterprise (SAP EWM, Manhattan, Blue Yonder) ở quy mô toàn cầu. Thị trường mục tiêu là kho vừa (vài trăm → vài chục nghìn vị trí) đang dùng Excel hoặc WMS cơ bản, nơi **block-stacking / deep-storage** (lô chồng/xếp sâu) gây lãng phí vận hành nhưng chưa có công cụ trực quan.

---

## 2. Vấn đề cốt lõi

| Vấn đề thực tế | Hậu quả | Hiện trạng |
|---|---|---|
| Lô xếp chồng/sâu, kích thước không đồng nhất | Lấy 1 lô buộc phải dời nhiều lô — tốn thời gian, dễ sai | Thủ công, dựa kinh nghiệm thủ kho |
| Vị trí trống không track chính xác | Nhập sai chỗ, không tối ưu không gian | Giấy, Excel, hoặc WMS chỉ track theo mã text |
| Không dự đoán "lô nào sẽ tốn công lấy ra" | Putaway tạo nợ vận hành tương lai | Hầu như không xử lý |
| Sai lệch hệ thống vs. thực tế | Kiểm kê sai, mất công dò tìm | Cycle count thủ công, độ trễ cao |

**Insight:** vấn đề không phải "khó nhìn thấy kho" — mà là **không ai tính trước thứ tự di chuyển tối ưu**. 3D chỉ giải quyết vấn đề nhận thức nếu đi kèm thuật toán ra quyết định. Vì vậy đây là **decision-support system có giao diện 3D**, không phải "3D viewer".

---

## 3. Triết lý thiết kế

1. **3D là lớp hiển thị, không phải nơi ra quyết định.** Người dùng không kéo-thả sắp xếp lô bằng tay — đó chính là cái lãng phí sản phẩm muốn loại bỏ. Engine tính toán mọi đề xuất; 3D trình chiếu và cho phép xác nhận.
2. **Thuật toán đi trước, giao diện đi sau.** CRP và putaway optimization là lõi giá trị. Thiếu phần này, sản phẩm chỉ là "warehouse viewer" mã nguồn mở.
3. **Dữ liệu thực tế là nguồn sự thật.** Bắt buộc scan tại mọi điểm chạm vật lý; không cập nhật tay tùy ý.
4. **Thiết kế cho quy mô tăng dần.** Render và data model chịu được từ vài trăm → vài chục nghìn vị trí mà không viết lại.

---

## 4. Đối tượng người dùng

| Vai trò | Nhu cầu | Giá trị |
|---|---|---|
| Thủ kho / vận hành | Biết làm gì, theo thứ tự nào | Danh sách bước di chuyển tối ưu, trực quan trên thiết bị cầm tay |
| Quản lý kho | Tối ưu không gian, giảm thời gian xử lý | Heatmap quá tải/trống, báo cáo hiệu suất putaway-picking |
| Quản lý vận hành | Quyết định layout, đầu tư mở rộng | Mô phỏng "what-if" trên layout 3D trước khi đổi thật |

---

## 5. Kiến trúc tổng thể

```
┌──────────────────────────────────────────────────────────┐
│  FRONTEND — Next.js + React Three Fiber (Three.js)         │
│  • 3D Scene (InstancedMesh — chịu hàng chục nghìn vị trí)   │
│  • Dashboard nghiệp vụ (đơn, tồn kho, báo cáo)             │
│  • Luồng xác nhận thao tác (scan → xác nhận di chuyển)     │
└──────────────────────────────────────────────────────────┘
                    ▲  ▼  REST (lệnh/truy vấn) + WebSocket (đẩy thay đổi)
┌──────────────────────────────────────────────────────────┐
│  BACKEND API                                               │
│  • Inventory Service   — tồn kho, SKU, lô hàng             │
│  • Putaway Engine      — SLAP: gợi ý vị trí nhập tối ưu    │
│  • Relocation Engine   — CRP: thứ tự di chuyển khi lấy lô  │
│  • Picking Service     — wave/zone picking, FIFO/FEFO      │
│  • Realtime Sync       — WebSocket đẩy delta tới 3D scene  │
└──────────────────────────────────────────────────────────┘
                    ▲  ▼
┌──────────────────────────────────────────────────────────┐
│  DATA — PostgreSQL (Supabase), database: `stockpile_3d`     │
│  • Movement ledger (append-only) = nguồn sự thật           │
│  • Bảng placement (projection của ledger) = trạng thái hiện │
│  • Index theo lane (zone,aisle,rack) cho truy vấn blocking │
└──────────────────────────────────────────────────────────┘
```

**Stack backend:** BACKEND API hiện thực bằng **Java + Spring Boot** — Spring Web (REST), Spring WebSocket (đẩy delta realtime), Spring Data JPA/Hibernate, **native query** cho truy vấn blocking nặng; migration schema bằng **Flyway**. PostgreSQL `stockpile_3d` giữ nguyên. *(Quyết định + đánh đổi ghi trong [ADR-0002](./adr/0002-backend-spring-boot.md).)*

**Nguyên tắc kiến trúc dữ liệu:** mọi thay đổi vật lý ghi vào **movement ledger append-only** (inbound, putaway, relocate, pick, outbound). Bảng `placement` (trạng thái hiện tại của từng lô) là **projection** dựng lại từ ledger. Khi nghi ngờ sai lệch, ledger là trọng tài — cho phép audit và replay, đồng thời là nền cho cycle count.

---

## 6. Mô hình dữ liệu cốt lõi

> Đây là phần backend đánh giá nặng nhất. Schema chi tiết (kiểu cột, ràng buộc, migration) để trong `docs/api-spec.md`; dưới đây là các thực thể và quan hệ định hình toàn bộ engine.

**Thực thể chính**

| Thực thể | Vai trò | Trường then chốt |
|---|---|---|
| `Location` (zone→aisle→rack→level→bin) | Khung không gian kho | toạ độ `(x,y,z)` góc, kích thước `(w,d,h)`, `lane_id`, `access_face` (hướng lấy hàng) |
| `Sku` | Master sản phẩm | `dims`, `weight`, `handling` (FIFO/FEFO) |
| `Lot` | Đơn vị vật lý đặt trong kho | `sku_id`, bounding box `(w,d,h)`, `weight`, `expiry`, `predicted_retrieval_at` |
| `Placement` | Lô đang chiếm vị trí nào | `lot_id`, `bin_id`, pose `(x,y,z)` — **projection từ ledger** |
| `Movement` | Bút toán vật lý (append-only) | `lot_id`, `type`, `from_bin`, `to_bin`, `ts`, `actor`, `scan_ref` |

**Quan hệ "blocking" — trái tim của hệ thống.** Lô **B chặn A** nếu để rút A ra theo `access_face` của lane, bắt buộc phải dời B trước. Định nghĩa hình học:
- **Chặn trên (on-top):** `B.z_min ≥ A.z_max` và hình chiếu `(x,y)` của B giao với A. *(Dùng `≥` cho cùng tầng là sai — xem Dev Log 2026-06-25 ở file 03.)*
- **Chặn trước (in-front):** cùng lane, B nằm gần `access_face` hơn A và phủ đường rút.

Tập quan hệ này tạo **đồ thị blocking có hướng** (DAG khi xếp hợp lệ). CRP làm việc trên đồ thị này.

**Quyết định khóa: không dùng PostGIS/R-tree toàn cục cho v1.** Quan hệ blocking là **cục bộ trong từng lane/stack** — không cần index không gian 3D toàn kho. Chỉ cần phân vùng theo `(zone, aisle, rack)` rồi suy luận trong lane (vài → vài chục lô). Điều này giữ truy vấn đơn giản, tránh phụ thuộc nặng (PostGIS) chưa cần ở quy mô mục tiêu, và để dành nâng cấp index không gian cho giai đoạn multi-warehouse. *(Sẽ ghi thành ADR riêng theo mẫu file 03.)*

---

## 7. Yêu cầu phi chức năng (NFR) — mục tiêu định lượng

Số liệu không phải cam kết tuyệt đối mà là **mục tiêu thiết kế** để mọi quyết định kỹ thuật có thước đo:

| Hạng mục | Mục tiêu | Vì sao |
|---|---|---|
| Quy mô | 10k–50k vị trí, ~5k–20k lô active | Định vị "kho vừa" |
| Tính CRP cho 1 lane | < 500 ms với lane ≤ 100 lô | Đủ "tương tác", người dùng không chờ |
| Render 3D | ~60 fps tới ~50k instance | InstancedMesh + frustum culling |
| Độ trễ sync | < 1 s từ scan → cập nhật scene | Cảm giác "thời gian thực" |
| Tính nhất quán | Ledger là nguồn sự thật; optimistic lock trên `bin` | Tránh 2 putaway vào cùng vị trí |

---

## 8. Các module chức năng

### 8.1. Mô hình không gian kho
Cấu trúc zone→aisle→rack→level→bin, mỗi vị trí gắn toạ độ + kích thước. Mỗi lô là bounding box, có thể chồng/lồng trong bin (block stacking).

### 8.2. Putaway Engine — bài toán SLAP
**Bài toán:** Storage Location Assignment Problem — chọn vị trí trống cho lô mới sao cho tổng chi phí vận hành kỳ vọng nhỏ nhất. Đây là lớp **NP-hard** ở dạng tổng quát.

**Cách giải v1 — chấm điểm greedy.** Với mỗi vị trí trống khả thi `c`, tính:
```
score(c) = w₁·dist_to_dock(c)
         + w₂·blocking_penalty(c, lô)        # phạt nếu lô mới sẽ chặn lô khác
         + w₃·retrieval_misalignment(c, lô)  # FEFO/turnover cao → vị trí dễ lấy
         + w₄·fit_penalty(c, lô)             # phạt lệch kích thước/tải trọng
```
Chọn `c` có `score` nhỏ nhất. **Độ phức tạp:** `O(F)` với F là số vị trí trống khả thi sau khi lọc theo lane/kích thước (thực tế F nhỏ nhờ tiền lọc), mỗi đánh giá `O(k)` với k lô lân cận → tổng `O(F·k)`.

**Trade-off:** greedy không tối ưu toàn cục, nhưng giải thích được cho người dùng và chạy real-time. Trọng số `wᵢ` để cấu hình theo kho.

### 8.3. Relocation Engine — bài toán CRP (lõi giá trị)
**Bài toán:** Container/Block Relocation Problem — cho lô mục tiêu cần rút, tìm chuỗi di chuyển **tối thiểu** để giải phóng nó, sao cho mỗi lô bị dời được đặt vào vị trí tạm hợp lệ không gây kẹt mới. Đây là kết quả **NP-hard đã biết** trong literature CRP (xem Caserta, Schwarze, Voß — survey container rehandling; và chứng minh độ phức tạp của Block Relocation Problem).

**Input:** lô mục tiêu + trạng thái lane. **Output:** danh sách bước `(lô, từ_vị_trí, tới_vị_trí)` theo thứ tự thực thi.

**Heuristic v1 — greedy (pseudocode rút gọn):**
```
function relocate(target, lane):
    G  = build_blocking_graph(lane)        # O(n²) ngây thơ; O(n log n) sau tối ưu
    steps = []
    while target còn bị chặn trong G:
        b = blocker ưu tiên dời       # chọn lô bị chặn ít nhất / dự đoán lấy muộn nhất
        dest = vị trí tạm gần nhất không tạo blocking mới cho b
        steps.append((b, b.bin, dest))
        cập nhật G sau khi dời b
    return steps
```
**Độ phức tạp:** dựng đồ thị blocking `O(n log n)` sau khi index theo lane (bản ngây thơ `O(n²)`); vòng giải tối đa `O(n)` bước, mỗi bước tìm dest `O(n)` → tổng `O(n²)` trong trường hợp xấu, với n = số lô trong lane (≤ ~100 theo NFR). Output được biến thành animation 3D — người dùng không phải tự suy luận.

**Trade-off đã biết:** greedy **không** đảm bảo số bước tối thiểu tuyệt đối (branch-and-bound / beam search tốt hơn nhưng chậm và khó diễn giải). Đủ tốt cho phần lớn ca thực tế; sẽ đánh giá lại nếu mở rộng kho rất lớn. *(Quyết định này ghi đầy đủ trong ADR-0001, file 03.)*

### 8.4. Picking & Order Management
Zone/wave picking để tránh nhân viên chồng lối đi; FIFO/FEFO cho hàng có hạn dùng.

### 8.5. 3D Visualization Layer
Render thời gian thực bằng InstancedMesh; heatmap mật độ; cảnh báo lệch tồn; mô phỏng "what-if" cho thay đổi layout.

### 8.6. Ground-Truth Sync
Scan bắt buộc mọi điểm chạm vật lý; cycle counting tích hợp vào luồng vận hành hàng ngày, không phải sự kiện riêng.

---

## 9. Roadmap

| Giai đoạn | Nội dung | Mục tiêu |
|---|---|---|
| **1 — MVP** | Data model + ledger, 3D viewer đọc dữ liệu tĩnh, CRUD tồn kho | Hiển thị kho 3D từ dữ liệu thật |
| **2 — Lõi thuật toán** | Relocation Engine (CRP greedy), Putaway Engine | Giải trực tiếp nỗi đau "lấy 1 lô phải dời nhiều lô" |
| **3 — Thời gian thực** | WebSocket sync, scan barcode, zone/wave picking | Vận hành thật, không chỉ demo |
| **4 — Phân tích & mở rộng** | Heatmap, báo cáo, what-if, multi-warehouse | Giá trị cho cấp quản lý, sẵn sàng nhân rộng |

---

## 10. Rủi ro

- **Độ chính xác dữ liệu — rủi ro lớn nhất.** Toạ độ/lô sai → mọi tính toán (CRP, SLAP) vô nghĩa. Cần đầu tư nghiêm túc vào scan + cycle count, không chỉ thuật toán.
- **Độ phức tạp vs. thời gian phản hồi.** Kho lớn cần kiểm soát heuristic theo NFR §7; tránh rơi vào NP-hard không kiểm soát.
- **Chi phí chuyển đổi vận hành.** Nhân viên phải đổi quy trình (bắt buộc scan, làm theo gợi ý). Cần UX đơn giản để giảm rào cản áp dụng.

---

*Bản tổng quan định hướng. Đặc tả thuật toán đầy đủ (pseudocode chi tiết + test case) sẽ nằm trong `docs/algorithm-spec.md`; schema chi tiết trong `docs/api-spec.md`; các quyết định lớn ghi trong `docs/adr/`.*
