# ADR-0002: Backend stack — Java + Spring Boot thay cho dự kiến Node/NestJS

## Trạng thái
Accepted — 2026-06-19

## Bối cảnh
Thiết kế ban đầu dự kiến backend chạy trên Node (NestJS) với ORM Prisma/Drizzle,
chia sẻ type với frontend qua `src/shared`. Tuy nhiên:

- Tôi đã có **nền tảng Spring Boot** sẵn — chọn stack này rút ngắn thời gian dựng
  và giảm rủi ro kỹ thuật so với học lại hệ sinh thái Node backend.
- **Spring Boot là stack backend được tuyển nhiều ở thị trường Việt Nam.**
- Dự án nhắm **portfolio xin intern backend**; backend Java + Spring Boot là tín
  hiệu phù hợp với mục tiêu nghề nghiệp đó.

## Quyết định
Backend hiện thực bằng **Java + Spring Boot**:

- **Spring Web** cho REST API (lệnh/truy vấn).
- **Spring Data JPA** (ORM = **JPA/Hibernate**) cho phần CRUD/đọc-ghi thông thường;
  **native query** cho các truy vấn blocking nặng (đồ thị blocking cục bộ theo lane,
  xem docs/01 §6) — nơi cần kiểm soát SQL sát phần cứng và hiệu năng.
- **Spring WebSocket** cho realtime sync (đẩy delta tới 3D scene).
- **PostgreSQL** database `stockpile_3d` **giữ nguyên** (không đổi data layer).
- Migration schema bằng **Flyway** (versioned SQL migrations).

## Hệ quả

Tích cực:
- Tận dụng kinh nghiệm Spring Boot sẵn có → dựng nhanh, ít rủi ro.
- Khả năng tuyển dụng cao ở thị trường VN; khớp mục tiêu portfolio intern backend.
- Hệ sinh thái Spring trưởng thành (security, data, validation, test).
- JPA cho năng suất ở phần thường + native query cho phần nóng → cân bằng tốc độ
  phát triển và kiểm soát hiệu năng cho CRP/blocking.

Tiêu cực / đánh đổi (không giấu):
- Dự án trở thành **đa ngôn ngữ** (frontend TypeScript, backend Java) →
  **mất khả năng dùng chung type/contract qua `src/shared`** như thiết kế Node ban đầu.
  → **Bù lại:** sinh **client TypeScript từ OpenAPI** của backend Spring để giữ
  type-safety xuyên BE-FE; `src/shared` chỉ còn giữ contract trung tính
  (ví dụ schema OpenAPI), không còn là TS dùng chung trực tiếp.
- Thêm **JVM + Gradle/Maven** vào toolchain; thời gian build và footprint nặng hơn
  Node.
- Người đọc repo phải nắm cả hai hệ sinh thái.
