# Changelog

Tất cả thay đổi đáng kể của dự án được ghi tại đây.

Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.0.0/);
version theo [SemVer](https://semver.org/lang/vi/).

## [Unreleased]

## [0.1.0] - 2026-06-21
MVP — Giai đoạn 1: hiển thị kho 3D từ dữ liệu thật.

### Added
- Bộ tài liệu nền (`docs/00`–`03`), từ điển thuật ngữ (`docs/glossary.md`), sơ đồ UML (`docs/diagrams.md` + Astah), `.gitignore`, `CHANGELOG`, `README`.
- Công cụ quy trình: slash command `/ship`, kế hoạch commit theo giai đoạn (`docs/commit-plan.md`).
- ADR-0002 (backend stack Java + Spring Boot), ADR-0003 (movement ledger append-only + placement là projection).
- Khung backend Spring Boot (`src/backend`): Java 25, Maven, package theo feature phân tầng controller→service→repository; cấu hình DB đọc từ biến môi trường; Flyway; springdoc-openapi; health endpoint `GET /api/health`.
- Khung frontend Next.js + TypeScript (`src/frontend`).
- Data model lõi (Flyway `V1`): 5 bảng `location`, `sku`, `lot`, `placement`, `movement` (ledger append-only), PK BIGINT identity, enum VARCHAR + CHECK, index theo lane; JPA entities + Spring Data repositories.
- Projection ledger→placement: `MovementService` (ghi bút toán append-only + cập nhật placement) và `PlacementProjectionService` (`apply` + `rebuildAll` replay từ ledger). Pose placement = tọa độ góc bin (v1).
- REST API tồn kho: CRUD cho `sku`, `location`, `lot`; `GET /api/placements` (read-only projection); `POST /api/movements` (ghi ledger). DTO tách entity, Bean Validation, xử lý lỗi 404/400 tập trung; CORS cho frontend dev.
- 3D viewer: React Three Fiber + Three.js render kho bằng `InstancedMesh` (khung kệ từ `location`, lô từ `placement`), OrbitControls. Chỉ hiển thị, không ra quyết định.
- Test (Testcontainers + MockMvc): migration, projection (gồm ca khẳng định incremental == ledger replay), và API tồn kho.
