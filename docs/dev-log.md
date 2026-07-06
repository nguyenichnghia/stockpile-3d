# Dev Log — Stockpile-3D

> Nhật ký vấn đề gặp phải + nguyên nhân + cách giải, viết cho chính mình (theo [03-documentation.md](./03-documentation.md) §5). Mới nhất ở trên.

## 2026-07-06 — Transfer trả `LazyInitializationException` dù test xanh

- **Vấn đề:** `TransferServiceTest` (Testcontainers) xanh 7/7, nhưng gọi `POST /api/transfers` thật thì trả **HTTP 500** `LazyInitializationException: Could not initialize proxy [Sku#1] - no session`. Lạ hơn: OUTBOUND *đã* ghi (lô rời kho A), chỉ bước map response mới nổ.
- **Nguyên nhân:** service `open()`/`receive()` trả **entity** `Transfer`, controller mới gọi `TransferDto.from(...)` — tức các proxy lazy (`lot.getSku()`, `warehouse`, `movement`) được truy cập **sau khi** transaction service đã đóng. `spring.jpa.open-in-view: false` (đặt từ đầu, đúng) nên không còn session ở tầng controller → nổ. Test **không** bắt được vì cả class `@Transactional`: session còn mở suốt test, `from()` init proxy thoải mái. Đây là bug **chỉ tồn tại ở ranh giới HTTP thật**.
- **Đã sửa:** map DTO **bên trong** transaction — service trả thẳng `TransferDto` (`TransferDto.from(...)` gọi trong method `@Transactional`), controller chỉ chuyển tiếp. Các service đọc khác (`ReportingService`, `WhatIfService`) vốn đã map-trong-transaction nên không dính; transfer là service mới nên lặp lại lỗi này.
- **Học được:** test `@Transactional` **che giấu** lỗi lazy-loading ở boundary vì nó giữ session mở — xanh test **không** chứng minh API chạy. Với `open-in-view=false`, quy tắc: **entity không rời tầng service**; map sang DTO trong transaction. Và: verify **chạy API thật** (không chỉ test) là thứ duy nhất lộ ra lớp bug này — giống bài học entry 2026-06-22.

## 2026-07-01 — Backend `password authentication failed` khi chạy local

- **Vấn đề:** chạy backend local (`mvnw spring-boot:run`) trỏ vào Postgres của Docker (`localhost:5432`) → Flyway/Hibernate báo `FATAL: password authentication failed for user "postgres"`, dù mật khẩu (`postgres/postgres`) khớp cấu hình compose và `docker exec psql` vào container thì đăng nhập được.
- **Nguyên nhân:** máy dev có **service Postgres cài sẵn trên Windows** (`postgresql-x64-18`) đang **chiếm cổng 5432**. Backend nối `localhost:5432` trúng Postgres Windows (mật khẩu khác), không phải container Docker. `netstat -ano | grep :5432` cho thấy **hai** tiến trình cùng LISTENING. `docker exec psql` không lộ ra vì nó nối *bên trong* container.
- **Đã sửa:** chạy Postgres của Docker ở **cổng 5433** (`-p 5433:5432`) và trỏ backend vào `jdbc:postgresql://localhost:5433/...`, tránh service Windows. (Testcontainers không bị ảnh hưởng — nó tự cấp cổng ngẫu nhiên.)
- **Học được:** khi lỗi auth "vô lý" dù mật khẩu đúng, nghi ngờ **nối nhầm server** trước khi nghi mật khẩu. Kiểm cổng đang lắng nghe (`netstat`) và test auth **qua TCP** (`psql -h 127.0.0.1`), không chỉ qua socket nội bộ container. Nếu muốn `docker compose up` chạy như tài liệu, cần tắt service Postgres Windows hoặc đổi cổng map trong `docker-compose.yml`.

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
