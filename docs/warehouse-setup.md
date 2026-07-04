# Thiết lập kho — các cách tạo dữ liệu vị trí (warehouse setup)

> **Tài liệu đề xuất (forward-looking).** Bàn về khâu **thiết lập kho** — cách tạo ra dữ liệu `location` (các bin/kệ với tọa độ x/y/z, kích thước, lane, access face) trước khi kho có thể chứa hàng và chạy các engine (CRP/SLAP). Đây là **tiền đề** của mọi tính năng khác: không có vị trí thì không có gì để đặt hàng, để tra cứu, để tô màu.
>
> **Tính chất:** *đề xuất để tham khảo/thảo luận*, **không phải** quyết định đã chốt. Quyết định thật (vd chọn định dạng import, làm editor 3D) nên thành [ADR](./adr/) riêng. Tài liệu ghi trung thực **trade-off** (đánh đổi) và **mức ưu tiên** (Now / Next / Later). Đọc cùng [data-model.md](./data-model.md) (bảng `location`) và [architecture.md](./architecture.md).

---

## 📖 Nói nôm na (đọc cái này trước)

Trước khi kho làm được việc gì, nó phải **có kệ đã** — tức là trong database (cơ sở dữ liệu) phải có sẵn danh sách các **ô chứa (bin)**, mỗi ô biết mình **nằm ở tọa độ nào, to bằng nào, thuộc lane (ngăn/làn) nào, lấy hàng ra từ hướng nào**.

Hiện tại muốn tạo kho, ta phải **gọi API tạo từng ô một** — một kho 50 kệ là hàng nghìn ô, làm tay không nổi. Tài liệu này liệt kê các cách làm cho khâu đó **nhanh và thực tế hơn**, từ nhẹ tới nặng:

1. **Sinh kho theo lưới (generator)** — nhập vài con số (mấy zone, mấy aisle, mấy tầng...) → máy tự đẻ ra hàng nghìn ô đều tăm tắp. *Nhẹ nhất, làm ngay.*
2. **Nạp từ file (import)** — kho thật khai báo trong Excel/CSV → nạp một phát vào database. *Khi có kho thật.*
3. **Trình vẽ trực quan (editor)** — kéo-thả dựng kệ ngay trên màn hình 3D. *Nặng, để sau.*

Mỗi cách đều ghi: *cần khi nào* và *được gì / mất gì*.

---

## 1. Hiện trạng — có gì, thiếu gì

### Đã có ✅
- **Bảng `location` đầy đủ** ([data-model.md](./data-model.md), [V1__core_schema.sql](../src/backend/src/main/resources/db/migration/V1__core_schema.sql)): mỗi ô có `zone/aisle/rack/level/bin` (mã phân cấp), tọa độ góc `x/y/z`, kích thước `w/d/h`, `lane_id`, `access_face` (hướng lấy hàng), ràng buộc UNIQUE trên mã ô **trong một kho** (từ V3/ADR-0009, mỗi location thuộc một `warehouse`).
- **API CRUD từng ô** ([LocationController.java](../src/backend/src/main/java/com/stockpile/inventory/controller/LocationController.java)): `POST /api/locations` tạo **một** vị trí, có validation.
- **Frontend đọc & vẽ** vị trí lên 3D ([Warehouse3D.tsx](../src/frontend/src/components/Warehouse3D.tsx)) — nhưng chỉ **hiển thị**, không tạo.

### Thiếu ❌ (khoảng trống này tài liệu bàn cách lấp)
- **Không có cách tạo hàng loạt.** Muốn kho 50k ô phải gọi API 50k lần → không thực tế.
- **Không có seed data (dữ liệu mẫu)** để chạy thử tính năng (tra cứu, heatmap...).
- **Không có import** từ file khai báo của kho thật.
- **Không có công cụ trực quan** để người không biết code dựng kho.

> Nói cách khác: mô hình dữ liệu đã sẵn sàng, nhưng **quy trình nạp dữ liệu vào** thì chưa có. Đó chính là "thiết lập kho".

---

## 2. Cách A — Sinh kho theo lưới (Grid Generator) `[Now]`

**Ý tưởng:** phần lớn kho block-stacking (chất khối) là **lưới đều** — N zone, mỗi zone M aisle, mỗi aisle K rack, mỗi rack L tầng (level), mỗi tầng P ô (bin). Thay vì khai từng ô, khai **các tham số lưới** rồi để code **tính tọa độ và sinh hàng loạt**.

**Đầu vào (ví dụ):**
```jsonc
{
  "zones": 2, "aislesPerZone": 4, "racksPerAisle": 6,
  "levelsPerRack": 4, "binsPerLevel": 3,
  "binSize":    { "w": 1.2, "d": 1.0, "h": 1.5 },  // kích thước 1 ô (mét)
  "aisleGap":   2.0,                               // lối đi giữa 2 aisle
  "accessFace": "SOUTH"                            // hướng lấy hàng mặc định
}
```
→ Sinh ra `2·4·6·4·3 = 576` bản ghi `location`, tọa độ `x/y/z` tính tự động từ chỉ số ô nhân kích thước + khoảng cách, `lane_id` gán theo `(zone, aisle, rack)` (đúng invariant "blocking cục bộ theo lane"), mã `zone/aisle/rack/level/bin` đánh số tuần tự.

**Cách hiện thực:** một service `WarehouseGeneratorService` + endpoint `POST /api/warehouses/{id}/generate` (tạo kho trước bằng `POST /api/warehouses` với `{code, name}`), **hoặc** một Flyway migration/seed script cho môi trường dev. Ghi tất cả trong **một transaction** (giao dịch — hoặc thành công hết, hoặc không gì). Guard "đã có location thì từ chối" tính **theo kho đích** (ADR-0009) — kho thứ hai/ba generate bình thường khi kho đó còn trống.

| | |
|---|---|
| **Được** | Rẻ (vài giờ code). Có ngay dữ liệu để test mọi tính năng. Lấp đúng khoảng trống lớn nhất hiện tại. |
| **Mất** | Chỉ hợp kho **đều đặn**; kho thật có góc khuyết, kệ đặc biệt thì generator không tả được → cần Cách B/C bổ sung. |
| **Ưu tiên** | **`[Now]`** — nên làm trước cả tính năng tra cứu, vì tra cứu cần kho để thử. |

> ⚠️ **Lưu ý invariant:** generator chỉ tạo **`location`** (khung không gian), **không** tạo `movement`/`placement`. Đặt hàng vào kho vẫn phải đi qua ledger (`MovementService`) — xem [ADR-0003](./adr/0003-ledger-projection.md). Đừng để generator ghi thẳng `placement`.

---

## 3. Cách B — Nạp từ file (Import CSV/Excel) `[Next]`

**Ý tưởng:** kho thật thường đã có bảng khai báo vị trí (Excel do quản kho lập). Cho phép **upload file → parse → tạo hàng loạt `location`**.

**Định dạng ví dụ (CSV):**
```csv
zone,aisle,rack,level,bin,x,y,z,w,d,h,lane_id,access_face
A,01,01,1,01,0.0,0.0,0.0,1.2,1.0,1.5,A-01-01,SOUTH
A,01,01,2,01,0.0,0.0,1.5,1.2,1.0,1.5,A-01-01,SOUTH
```

**Cách hiện thực:** endpoint `POST /api/warehouse/import` nhận `multipart/file`; parse (Apache Commons CSV / Apache POI cho Excel); **validate từng dòng** (tọa độ hợp lệ, mã không trùng, access_face thuộc enum) rồi ghi trong một transaction; trả về báo cáo `{ số dòng OK, danh sách dòng lỗi + lý do }`.

| | |
|---|---|
| **Được** | Nạp được kho thật **không đều** (mọi ô khai tường minh). Người dùng nghiệp vụ tự chuẩn bị file được. |
| **Mất** | Người dùng phải **tự tính tọa độ** trong file — dễ sai; cần validate kỹ và báo lỗi rõ. Kho lớn thì file to. |
| **Ưu tiên** | **`[Next]`** — khi bắt đầu có kho thật cần nạp. Trước đó generator (A) là đủ cho dev/demo. |

> 💡 **Kết hợp A + B:** generator sinh khung đều → export ra CSV → người dùng chỉnh tay vài ô đặc biệt → import lại. Được cả nhanh lẫn linh hoạt.

---

## 4. Cách C — Trình vẽ trực quan (Layout Editor) `[Later]`

**Ý tưởng:** người dùng **kéo-thả dựng kệ ngay trên giao diện** (2D top-down hoặc 3D), hệ thống tự sinh tọa độ. Không cần biết code, không cần tính tọa độ tay.

**Hai mức:**
- **2D top-down (nhìn từ trên xuống)** — dễ hơn: vẽ ô lưới trên mặt bằng (floor plan), gán số tầng để nhân theo chiều cao. Đủ cho phần lớn nhu cầu.
- **3D editor** — kéo-thả khối trong không gian 3D (tái dùng React Three Fiber sẵn có). Trực quan nhất nhưng khó (raycasting để chọn/đặt, snap-to-grid — hít vào lưới, undo/redo).

**Lưu ý kiến trúc:** đây là chỗ **3D được phép "tạo" dữ liệu** — nhưng chỉ tạo **`location`** (khung kho), **không** vi phạm invariant "3D không quyết định điều phối hàng". Đặt/di chuyển *hàng* vẫn do engine + ledger. Nên tách rõ **chế độ Setup (dựng kho)** khác **chế độ Vận hành (xem/điều phối hàng)**.

| | |
|---|---|
| **Được** | Trải nghiệm tốt nhất; người không kỹ thuật tự thiết lập được; tận dụng thế mạnh 3D của dự án. |
| **Mất** | **Nặng — gần như một dự án con** (state management, raycasting, snap, undo/redo, lưu nháp). Over-engineering nếu làm sớm. |
| **Ưu tiên** | **`[Later]`** — chỉ khi sản phẩm trưởng thành và có nhu cầu người dùng tự dựng kho. Generator + import gánh được giai đoạn đầu. |

---

## 5. Hướng ngoài luồng — dựng kho từ quét thực tế `[Nghiên cứu / Later]`

Có hướng "quay video/quét LiDAR kho thật → tự dựng mô hình 3D" (photogrammetry / 3D reconstruction — tái dựng 3D từ ảnh). Về lý thuyết có thể suy ra vị trí kệ từ point cloud (đám mây điểm).

**Đánh giá thẳng:** **không đáng cho dự án này lúc này.** Đó là một lĩnh vực thị giác máy tính/học sâu riêng (cần GPU, mô hình lớn), cho ra **mô hình hình học thô** chứ không ra được **cấu trúc logic** (mã zone/aisle/rack, lane, access_face) mà hệ thống cần. Khoảng cách từ "point cloud" tới "bảng `location` có ngữ nghĩa" là rất xa. Ghi lại ở đây để **loại trừ có cân nhắc**, không phải để làm.

---

## 6. Ưu tiên (lộ trình thực tế)

| Ưu tiên | Cách | Vì sao |
|---|---|---|
| **Now** | **A — Grid generator** | Rẻ, lấp khoảng trống lớn nhất, tạo dữ liệu để test mọi tính năng khác (gồm tra cứu/heatmap). |
| **Next** | **B — Import CSV/Excel** | Khi có kho thật (không đều) cần nạp. Kết hợp với A (generate → export → sửa → import). |
| **Later** | **C — Layout editor 3D/2D** | Trải nghiệm cao nhất nhưng tốn kém; chỉ khi sản phẩm trưởng thành. |
| **Loại trừ** | Dựng từ quét thực tế | Sai lĩnh vực, chi phí quá lớn so với giá trị. |

---

## 7. Nguyên tắc khi làm

1. **Chỉ tạo `location`, không đụng ledger.** Thiết lập kho = dựng khung không gian. Đưa *hàng* vào kho luôn qua `movement` → `placement` ([ADR-0003](./adr/0003-ledger-projection.md)).
2. **Ghi hàng loạt trong một transaction** — sinh/import 10k ô phải nguyên tử (all-or-nothing), tránh kho nửa vời khi lỗi giữa chừng.
3. **Validate mạnh** — tọa độ không âm/không chồng lấn vô lý, mã không trùng (đã có UNIQUE ở DB), `access_face` thuộc enum. Báo lỗi theo dòng cho import.
4. **`lane_id` sinh nhất quán** theo `(zone, aisle, rack)` để giữ đúng invariant "blocking cục bộ theo lane".
5. **Quyết định lớn → ADR.** Chọn định dạng import chuẩn, hay quyết làm editor, nên thành ADR riêng.

---

## 8. Liên quan
- [data-model.md](./data-model.md) — chi tiết bảng `location` (nguồn sự thật của cấu trúc dữ liệu).
- [architecture.md](./architecture.md) — hiện trạng hệ thống, API reference.
- [ADR-0003](./adr/0003-ledger-projection.md) — vì sao đặt hàng phải qua ledger (đừng để setup ghi thẳng placement).
- [system-design-proposal.md](./system-design-proposal.md) — mở rộng theo hướng *scale/hạ tầng* (khác tài liệu này, vốn về *thiết lập dữ liệu kho*).
- [glossary.md](./glossary.md) — thuật ngữ (lane, access face, transaction, point cloud...).
