# Stockpile-3D

> Hệ thống **hỗ trợ ra quyết định cho kho hàng, có giao diện 3D** — không phải "3D viewer". Lớp 3D chỉ trực quan hóa và xác nhận; **engine tối ưu ở backend mới là nơi ra quyết định**.

## Vấn đề

WMS truyền thống coi tồn kho là các dòng trong bảng (số lượng, SKU, vị trí dạng text "Kệ A-12-3"), tạo khoảng cách giữa dữ liệu hệ thống và thực tế vật lý: người vận hành không thấy lô nào đang chặn lô nào, không gian trống thực sự ở đâu, hay lấy 1 lô sẽ kéo theo bao nhiêu việc phụ.

Với kho **block-stacking / deep-storage** (lô xếp chồng, xếp sâu), insight cốt lõi là: vấn đề không phải "khó nhìn thấy kho" mà là **không ai tính trước thứ tự di chuyển tối ưu**. Stockpile-3D giải đúng nỗi đau đó — engine tính toán đề xuất, 3D trình chiếu, người dùng xác nhận.

Quy mô mục tiêu: kho vừa, 10k–50k vị trí, ~5k–20k lô active.

## Kiến trúc tổng thể

```
┌────────────────────────────────────────────────┐
│  FRONTEND — Next.js + React Three Fiber          │
│  • 3D scene (InstancedMesh) • dashboard nghiệp vụ│
└────────────────────────────────────────────────┘
            ▲  ▼  REST (lệnh/truy vấn) + WebSocket (đẩy delta)
┌────────────────────────────────────────────────┐
│  BACKEND — Java + Spring Boot                     │
│  • Inventory • Putaway Engine (SLAP)             │
│  • Relocation Engine (CRP) • Picking • Realtime  │
└────────────────────────────────────────────────┘
            ▲  ▼
┌────────────────────────────────────────────────┐
│  DATA — PostgreSQL (db: stockpile_3d)            │
│  • Movement ledger (append-only) = nguồn sự thật │
│  • placement = projection dựng lại từ ledger     │
└────────────────────────────────────────────────┘
```

Nguyên tắc dữ liệu: mọi thay đổi vật lý ghi vào **movement ledger append-only**; bảng `placement` là **projection** dựng lại từ ledger — khi nghi ngờ sai lệch, ledger là trọng tài. Chi tiết: [docs/01-overview.md](docs/01-overview.md).

## Công nghệ & lý do chọn

| Lớp | Công nghệ | Vì sao |
|---|---|---|
| Frontend | Next.js + React Three Fiber (Three.js) | `InstancedMesh` + frustum culling đạt ~60 fps tới ~50k instance — render được kho lớn mà không viết lại WebGL thủ công |
| Backend | **Java + Spring Boot** (Web, Data JPA, WebSocket, Flyway) | Hệ sinh thái trưởng thành; JPA cho CRUD + **native query** cho truy vấn blocking nặng; chốt trong [ADR-0002](docs/adr/0002-backend-spring-boot.md) |
| Data | PostgreSQL | Ledger append-only + projection; blocking suy luận **cục bộ theo lane**, không cần PostGIS ở v1 |
| API docs | springdoc-openapi | Sinh OpenAPI → tạo client TS cho frontend (BE Java, FE TS không dùng chung type) |

## Cách chạy

**Yêu cầu:** JDK 25 · Maven · Node.js · PostgreSQL.

### Cách nhanh nhất — Docker Compose (1 lệnh)
Chạy cả Postgres + backend + frontend bằng một lệnh (cần Docker Desktop):
```bash
docker compose up --build
```
- Frontend (3D viewer): http://localhost:3000
- Backend API: http://localhost:8080 · Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/api/health → `{"status":"UP"}`

Đổi mật khẩu DB qua biến môi trường `POSTGRES_PASSWORD` (mặc định `postgres` chỉ cho dev). Dừng: `docker compose down` (thêm `-v` để xóa luôn dữ liệu DB).

---

### Hoặc chạy thủ công (không Docker)

#### 1. Database
```bash
createdb stockpile_3d
```

#### 2. Backend (`src/backend`)
```bash
cd src/backend
cp .env.example .env.local          # rồi điền DB_USER / DB_PASSWORD (đã gitignore)

# nạp env (PowerShell):
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/stockpile_3d"
$env:DB_USER="postgres"; $env:DB_PASSWORD="<password>"

./mvnw spring-boot:run
```
Kiểm tra app đã lên:
- `GET http://localhost:8080/api/health` → `{"status":"UP"}`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

#### 3. Frontend (`src/frontend`)
```bash
cd src/frontend
npm install
npm run dev          # http://localhost:3000
```

> Backend đọc toàn bộ secret từ biến môi trường — **không hardcode** trong mã. Xem thêm [src/backend/README.md](src/backend/README.md).

## Điểm kỹ thuật nổi bật

- **CRP — Container/Block Relocation Problem** (Relocation Engine): cho lô mục tiêu cần rút, tính chuỗi di chuyển **tối thiểu** để giải phóng nó. Bài toán **NP-hard**; v1 dùng **heuristic greedy** trên đồ thị blocking cục bộ theo lane (mục tiêu < 500 ms cho lane ≤ 100 lô). Output là danh sách bước → animation 3D.
- **SLAP — Storage Location Assignment Problem** (Putaway Engine): chấm điểm các vị trí trống và chọn vị trí chi phí thấp nhất (`O(F·k)`), có thể giải thích cho người vận hành.

Đặc tả đầy đủ (pseudocode, Big-O, test case): [docs/algorithm-spec.md](docs/algorithm-spec.md). Tổng quan: [docs/01-overview.md](docs/01-overview.md) §8.

## Tài liệu

- [docs/00-index.md](docs/00-index.md) — mục lục toàn bộ tài liệu
- [docs/01-overview.md](docs/01-overview.md) — tổng quan, data model, NFR, thuật toán
- [docs/02-git-workflow.md](docs/02-git-workflow.md) · [docs/03-documentation.md](docs/03-documentation.md) — quy trình
- [docs/adr/](docs/adr/) — Architecture Decision Records

## Trạng thái

Đang ở giai đoạn **khung dự án** (chạy được, chưa có logic nghiệp vụ). Lộ trình & kế hoạch commit: [docs/commit-plan.md](docs/commit-plan.md).
