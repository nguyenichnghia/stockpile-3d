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
| Backend | **Java + Spring Boot** (Web, Data JPA, Flyway; WebSocket *dự kiến*) | Hệ sinh thái trưởng thành; JPA cho CRUD + **native query** cho truy vấn blocking nặng; chốt trong [ADR-0002](docs/adr/0002-backend-spring-boot.md) |
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

> ⚠️ **Xung đột cổng 5432:** nếu máy đã có sẵn một Postgres khác (ví dụ service `postgresql-x64` cài trên Windows) chiếm cổng 5432, backend sẽ nối nhầm vào đó và báo `password authentication failed` dù mật khẩu đúng. Cách xử lý: chạy Postgres của Docker ở cổng khác, ví dụ `docker run -d --name pg -e POSTGRES_DB=stockpile_3d -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5433:5432 postgres:18-alpine`, rồi trỏ backend vào `localhost:5433`. Chi tiết: [Dev Log 2026-07-01](docs/dev-log.md).

### Tạo dữ liệu mẫu để xem tính năng

Kho mới trống. Sinh nhanh một lưới rồi đặt vài lô (backend ở cổng 8080):
```bash
# sinh 1 cột 3 tầng (3 ô xếp chồng — để xem heatmap "độ bị chặn")
curl -X POST localhost:8080/api/warehouse/generate -H 'Content-Type: application/json' \
  -d '{"zones":1,"aislesPerZone":1,"racksPerAisle":1,"levelsPerRack":3,"binsPerLevel":1,"binWidth":1.2,"binDepth":1,"binHeight":1.5,"aisleGap":2,"accessFace":"SOUTH"}'
# tạo SKU rồi putaway lô vào các ô (xem docs/warehouse-setup.md để biết thêm)
```
Sau đó mở http://localhost:3000 và thử ô "Tra mã hàng", "Tra mã ô", dropdown "Heatmap…".

## Điểm kỹ thuật nổi bật

- **CRP — Container/Block Relocation Problem** (Relocation Engine): cho lô mục tiêu cần rút, tính chuỗi di chuyển **tối thiểu** để giải phóng nó. Bài toán **NP-hard**; v1 dùng **heuristic greedy** trên đồ thị blocking cục bộ theo lane (mục tiêu < 500 ms cho lane ≤ 100 lô). Output là danh sách bước → animation 3D.
- **SLAP — Storage Location Assignment Problem** (Putaway Engine): chấm điểm các vị trí trống và chọn vị trí chi phí thấp nhất (`O(F·k)`), có thể giải thích cho người vận hành.

Đặc tả đầy đủ (pseudocode, Big-O, test case): [docs/algorithm-spec.md](docs/algorithm-spec.md). Tổng quan: [docs/01-overview.md](docs/01-overview.md) §8.

## Tính năng đã có

| Nhóm | Tính năng | API |
|---|---|---|
| Tồn kho | CRUD `sku`/`location`/`lot`, ghi ledger, projection placement | `/api/skus` · `/api/locations` · `/api/lots` · `/api/movements` · `/api/placements` |
| Engine | Relocation (CRP) · Putaway (SLAP) — chỉ đề xuất, không ghi ledger | `/api/relocation-plan` · `/api/putaway-suggestion` |
| Thiết lập kho | Sinh kho theo lưới (tạo hàng loạt `location`) | `POST /api/warehouse/generate` |
| Tra cứu trực quan | Tra mã hàng (SKU) → highlight + làm mờ + nhãn ô · Tra mã ô → tô sáng khung ô (kể cả trống) | `GET /api/lots/locate?sku=` · `GET /api/locations/locate?code=` |
| Heatmap | Tô màu cả kho theo mức đầy / độ bị chặn / sắp hết hạn | `GET /api/heatmap?metric={fill\|blocking\|expiry}` |

Cách dùng trên 3D: ô nhập "Tra mã hàng" / "Tra mã ô" và dropdown "Heatmap…" ở góc trên trái màn hình.

## Tài liệu

- [docs/00-index.md](docs/00-index.md) — mục lục toàn bộ tài liệu
- [docs/01-overview.md](docs/01-overview.md) — tổng quan, data model, NFR, thuật toán
- [docs/02-git-workflow.md](docs/02-git-workflow.md) · [docs/03-documentation.md](docs/03-documentation.md) — quy trình
- [docs/adr/](docs/adr/) — Architecture Decision Records

## Trạng thái

Đã có **backend + frontend chạy được** với hai engine lõi và lớp tra cứu trực quan (tag `v0.1.0` MVP, `v0.2.0` lõi thuật toán; Giai đoạn 3 đang hoàn thiện). **Chưa làm:** engine picking và lớp realtime (WebSocket đẩy delta) — hiện frontend fetch một lần, không tự cập nhật. Lộ trình & tiến độ: [CHANGELOG](CHANGELOG.md) · [docs/commit-plan.md](docs/commit-plan.md).
