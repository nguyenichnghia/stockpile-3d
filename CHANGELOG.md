# Changelog

Tất cả thay đổi đáng kể của dự án được ghi tại đây.

Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.0.0/);
version theo [SemVer](https://semver.org/lang/vi/).

## [Unreleased]

### Added
- Bộ tài liệu nền (`docs/00`–`03`), `.gitignore`, `CHANGELOG`.
- Công cụ quy trình: slash command `/ship`, kế hoạch commit theo giai đoạn (`docs/commit-plan.md`).
- ADR-0002: chốt backend stack Java + Spring Boot.
- Khung backend Spring Boot (`src/backend`): Java 25, Maven, cấu trúc package theo feature (inventory/relocation/putaway/picking/realtime) phân tầng controller→service→repository; cấu hình DB đọc từ biến môi trường; Flyway sẵn sàng; springdoc-openapi.
- Health endpoint `GET /api/health`.
- Khung frontend Next.js + TypeScript (`src/frontend`).
- Data model lõi (Flyway `V1`): 5 bảng `location`, `sku`, `lot`, `placement`, `movement` (ledger append-only), khóa chính BIGINT identity, enum dạng VARCHAR + CHECK, index theo lane.
- JPA entities + Spring Data repositories cho 5 thực thể (`inventory/domain`, `inventory/repository`).
- Test migration bằng Testcontainers (PostgreSQL) — verify Flyway áp schema + entity mapping hợp lệ.
- Projection ledger→placement: `MovementService` (ghi bút toán append-only + cập nhật placement) và `PlacementProjectionService` (`apply` + `rebuildAll` replay từ ledger). Pose placement = tọa độ góc bin (v1).
- ADR-0003: movement ledger append-only + placement là projection.
- Test projection (Testcontainers) gồm ca khẳng định incremental == ledger replay.
- REST API tồn kho: CRUD cho `sku`, `location`, `lot`; `GET /api/placements` (read-only projection); `POST /api/movements` (ghi ledger append-only). DTO tách entity, Bean Validation, xử lý lỗi 404/400 tập trung (`ApiExceptionHandler`).
- Integration test API bằng MockMvc + Testcontainers (luồng Putaway → placement, 404, validation, delete).
