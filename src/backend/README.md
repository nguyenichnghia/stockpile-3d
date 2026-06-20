# stockpile-backend

Spring Boot backend cho Stockpile-3D. Khung chạy được, **chưa có logic nghiệp vụ**
(chưa entity, chưa migration). Stack chốt trong [ADR-0002](../../docs/adr/0002-backend-spring-boot.md):
Java 25 · Maven · Spring Web · Spring Data JPA · Spring WebSocket · Flyway · springdoc-openapi · Lombok.

## Yêu cầu
- JDK 25
- PostgreSQL với database `stockpile_3d`

## Cấu hình (không hardcode secret)
App đọc kết nối DB từ biến môi trường. Copy mẫu rồi điền giá trị thật:

```bash
cp .env.example .env.local   # .env.local đã được gitignore
```

| Biến | Ý nghĩa | Ví dụ |
|---|---|---|
| `DATABASE_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/stockpile_3d` |
| `DB_USER` | user Postgres | `postgres` |
| `DB_PASSWORD` | mật khẩu | — |

Tạo DB nếu chưa có:

```bash
createdb stockpile_3d
```

## Build & chạy

```bash
./mvnw clean package        # build (test full-context đang @Disabled tới khi có test DB)
./mvnw spring-boot:run      # cần biến môi trường ở trên + Postgres đang chạy
```

Nạp env trước khi chạy (PowerShell):

```powershell
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/stockpile_3d"; $env:DB_USER="postgres"; $env:DB_PASSWORD="..."
./mvnw spring-boot:run
```

## Kiểm tra app đã lên
- Health (app-owned): `GET http://localhost:8080/api/health` → `{"status":"UP"}`
- Health (actuator): `GET http://localhost:8080/actuator/health`
- API docs: `http://localhost:8080/v3/api-docs` · Swagger UI: `http://localhost:8080/swagger-ui.html`

## Cấu trúc package (theo feature, phân tầng)
```
com.stockpile
├── StockpileBackendApplication
├── common/            # config dùng chung + HealthController
├── inventory/         # controller / service / repository
├── relocation/        #   (CRP — Relocation Engine)
├── putaway/           #   (SLAP — Putaway Engine)
├── picking/
└── realtime/          #   (chỗ cho WebSocket)
```
Các package tầng hiện rỗng (giữ bằng `.gitkeep`) — sẽ thêm code khi vào Phase 1/2 theo [docs/commit-plan.md](../../docs/commit-plan.md).
