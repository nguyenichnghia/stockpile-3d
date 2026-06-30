# Sơ đồ thiết kế — Stockpile-3D

> Sơ đồ trực quan (Mermaid — xem trực tiếp trên GitHub) cho data model, kiến trúc, và **2 thuật toán lõi**. Phần class/ERD/component có thêm mô hình Astah ở [`diagrams/stockpile-3d.asta`](./diagrams/stockpile-3d.asta) (mở bằng Astah để chỉnh/export ảnh).
>
> **Mục lục:**
> - Cấu trúc: §1 Class domain · §2 ERD gọn · §3 Class service · §4 Component
> - Nghiệp vụ: §5 Use case · §6 Activity · §7 Sequence (putaway) · §8 **State — vòng đời lô**
> - Thuật toán: §9 **Flowchart CRP** · §10 **Sequence CRP (đề xuất→xác nhận)** · §11 **Flowchart SLAP**
> - Tham chiếu: §12 **ERD đầy đủ cột**
>
> Nguồn sự thật về thiết kế: [01-overview.md](./01-overview.md) §6, [data-model.md](./data-model.md), [algorithm-spec.md](./algorithm-spec.md), [architecture.md](./architecture.md) và các [ADR](./adr/).

## 1. Class diagram — Domain (entities)

Phản ánh các entity trong `src/backend/.../inventory/domain`. `placement` là
projection từ ledger; `movement` là ledger append-only (xem [ADR-0003](./adr/0003-ledger-projection.md)).

```mermaid
classDiagram
    class Location {
        +Long id
        +String zone
        +String aisle
        +String rack
        +String level
        +String bin
        +BigDecimal x, y, z
        +BigDecimal w, d, h
        +String laneId
        +AccessFace accessFace
    }
    class Sku {
        +Long id
        +String code
        +String name
        +BigDecimal w, d, h
        +BigDecimal weight
        +HandlingType handling
    }
    class Lot {
        +Long id
        +BigDecimal w, d, h
        +BigDecimal weight
        +LocalDate expiry
        +Instant predictedRetrievalAt
    }
    class Placement {
        +Long id
        +BigDecimal x, y, z
    }
    class Movement {
        +Long id
        +MovementType type
        +Instant ts
        +String actor
        +String scanRef
    }
    class HandlingType {
        <<enumeration>>
        FIFO
        FEFO
    }
    class AccessFace {
        <<enumeration>>
        NORTH
        SOUTH
        EAST
        WEST
        TOP
    }
    class MovementType {
        <<enumeration>>
        INBOUND
        PUTAWAY
        RELOCATE
        PICK
        OUTBOUND
    }

    Lot "*" --> "1" Sku : sku
    Placement "0..1" --> "1" Lot : lot
    Placement "*" --> "1" Location : bin
    Movement "*" --> "1" Lot : lot
    Movement "*" --> "0..1" Location : fromBin
    Movement "*" --> "0..1" Location : toBin
    Sku --> HandlingType
    Location --> AccessFace
    Movement --> MovementType
```

## 2. ERD — bảng PostgreSQL (Flyway V1)

Khóa chính BIGINT identity; enum lưu VARCHAR + CHECK; `placement.lot_id` UNIQUE
(một lô chiếm tối đa một vị trí).

```mermaid
erDiagram
    location ||--o{ placement : "bin_id"
    location ||--o{ movement  : "from_bin / to_bin"
    sku      ||--o{ lot       : "sku_id"
    lot      ||--|| placement : "lot_id (UNIQUE)"
    lot      ||--o{ movement  : "lot_id"

    location {
        bigint id PK
        varchar zone
        varchar aisle
        varchar rack
        varchar level
        varchar bin
        numeric x_y_z
        numeric w_d_h
        varchar lane_id
        varchar access_face
    }
    sku {
        bigint id PK
        varchar code UK
        varchar name
        numeric w_d_h
        numeric weight
        varchar handling
    }
    lot {
        bigint id PK
        bigint sku_id FK
        numeric w_d_h
        numeric weight
        date expiry
        timestamptz predicted_retrieval_at
    }
    placement {
        bigint id PK
        bigint lot_id FK_UK
        bigint bin_id FK
        numeric x_y_z
    }
    movement {
        bigint id PK
        bigint lot_id FK
        varchar type
        bigint from_bin FK
        bigint to_bin FK
        timestamptz ts
        varchar actor
        varchar scan_ref
    }
```

## 3. Class diagram — Service (ledger → projection)

Một quy tắc `apply()` dùng chung cho cả ghi incremental lẫn `rebuildAll()` replay,
nên hai con đường không thể lệch nhau. *(Thuộc PR `feature/ledger-projection`.)*

```mermaid
classDiagram
    class MovementService {
        +Movement record(Movement)
    }
    class PlacementProjectionService {
        +void apply(Movement)
        +void rebuildAll()
    }
    class MovementRepository {
        <<interface>>
        +findAllByOrderByTsAscIdAsc()
    }
    class PlacementRepository {
        <<interface>>
        +findByLotId(Long)
        +deleteByLotId(Long)
    }

    MovementService --> MovementRepository : append-only save
    MovementService --> PlacementProjectionService : apply (incremental)
    PlacementProjectionService --> PlacementRepository
    PlacementProjectionService --> MovementRepository : replay
```

## 4. Component / kiến trúc tổng thể

```mermaid
flowchart TB
    FE["Frontend — Next.js + React Three Fiber<br/>(3D scene: InstancedMesh)"]
    BE["Backend — Spring Boot<br/>Inventory · Putaway(SLAP) · Relocation(CRP) · Picking · Realtime"]
    DB[("PostgreSQL: stockpile_3d<br/>movement ledger (append-only) + placement (projection)")]

    FE -- "REST (lệnh/truy vấn)" --> BE
    BE -- "WebSocket (đẩy delta)" --> FE
    BE -- "Spring Data JPA + Flyway" --> DB
```

## 5. Use case — vai trò & chức năng

```mermaid
flowchart LR
    operator(["Thủ kho / vận hành"])
    manager(["Quản lý kho"])

    subgraph Stockpile-3D
        uc1(["Nhập kho / Putaway"])
        uc2(["Lấy hàng / Pick"])
        uc3(["Xem chuỗi di chuyển tối ưu (CRP)"])
        uc4(["Gợi ý vị trí cất (SLAP)"])
        uc5(["Xem kho 3D"])
        uc6(["Heatmap / báo cáo"])
    end

    operator --> uc1
    operator --> uc2
    operator --> uc3
    operator --> uc5
    uc1 --> uc4
    manager --> uc5
    manager --> uc6
```

## 6. Activity — ghi một movement + cập nhật projection

```mermaid
flowchart TD
    A(["Bắt đầu: có thay đổi vật lý"]) --> B["Tạo Movement (type, lot, from/to bin)"]
    B --> C["MovementService.record"]
    C --> D["Append vào ledger (movement)"]
    D --> E{"Loại movement?"}
    E -- PUTAWAY/INBOUND --> F["Upsert placement tại toBin<br/>pose = góc bin"]
    E -- RELOCATE --> G["Đổi bin của placement"]
    E -- PICK/OUTBOUND --> H["Xóa placement của lot"]
    F --> I(["Kết thúc: placement đồng bộ"])
    G --> I
    H --> I
```

## 7. Sequence — luồng Putaway (incremental projection)

```mermaid
sequenceDiagram
    actor User as Thủ kho
    participant MS as MovementService
    participant MR as MovementRepository
    participant PS as PlacementProjectionService
    participant PR as PlacementRepository

    User->>MS: record(PUTAWAY, lot, toBin)
    Note over MS,MR: append-only
    MS->>MR: save(movement)
    MR-->>MS: saved
    MS->>PS: apply(movement)
    PS->>PR: findByLotId(lot)
    PR-->>PS: empty / existing
    PS->>PR: save(placement @ toBin)
    PS-->>MS: done
    MS-->>User: Movement đã ghi
```

## 8. State diagram — vòng đời một lô (lot lifecycle)

Lô đi qua các trạng thái theo loại movement. CRP/SLAP làm việc ở trạng thái `Placed`.

```mermaid
stateDiagram-v2
    [*] --> InWarehouse: INBOUND
    InWarehouse --> Placed: PUTAWAY
    Placed --> Picked: PICK
    Picked --> [*]: OUTBOUND
    note right of Placed
        Có placement (chiếm 1 bin).
        RELOCATE = đổi bin, vẫn ở Placed.
        Có thể chặn / bị chặn lô khác.
    end note
    note left of InWarehouse
        Đã vào kho, có thể chưa cất
        (staging — chưa có placement).
    end note
```

## 9. Flowchart — thuật toán CRP (Relocation) từng bước

Vòng lặp greedy: dời blocker trực tiếp ưu tiên nhất tới vị trí tạm, lặp đến khi lô đích hết bị chặn.

```mermaid
flowchart TD
    A([plan lotId]) --> B{"placement của lot tồn tại?"}
    B -- không --> B404[/"404 NotFound"/]
    B -- có --> C["Load tất cả lô trong lane → List LotBox"]
    C --> D{"target còn blocker trực tiếp?"}
    D -- không --> E([Trả RelocationPlan steps])
    D -- có --> F["pickBlocker: chọn lô lấy-muộn-nhất,<br/>tie-break z cao nhất"]
    F --> G{"tìm vị trí tạm<br/>không tạo blocking mới?"}
    G -- không có --> G400[/"400 IllegalState"/]
    G -- có --> H["thêm step (lô, binCũ, dest)"]
    H --> I["mô phỏng dời lô in-memory"]
    I --> D
```

## 10. Sequence — CRP đề xuất rồi người dùng xác nhận (luồng đầy đủ)

Thể hiện tách bạch "đề xuất" (chỉ đọc) và "thực thi" (người dùng xác nhận → ghi ledger).

```mermaid
sequenceDiagram
    actor U as Operator
    participant FE as 3D Scene
    participant RC as RelocationController
    participant RS as RelocationService
    participant DB as PostgreSQL

    U->>FE: muốn lấy lô A
    FE->>RC: GET /api/relocation-plan?lotId=A
    RC->>RS: plan(A)
    RS->>DB: đọc placement + lô trong lane (read-only)
    RS-->>RC: RelocationPlan [steps]
    RC-->>FE: 200 + các bước
    FE-->>U: hiển thị animation các bước dời
    Note over U,FE: Engine chỉ ĐỀ XUẤT. Chưa có gì thay đổi.
    U->>FE: xác nhận thực hiện từng bước
    FE->>RC: POST /api/movements (RELOCATE ...) cho mỗi bước
    Note over RC,DB: lúc này mới ghi ledger + cập nhật placement
```

## 11. Flowchart — thuật toán SLAP (Putaway) chấm điểm

```mermaid
flowchart TD
    A([suggest lotId]) --> B{"lot tồn tại?"}
    B -- không --> B404[/"404"/]
    B -- có --> C["Load các location trống"]
    C --> D["Với mỗi location c:"]
    D --> E{"lô có VỪA bin c?<br/>(w,d,h ≤ bin)"}
    E -- không --> D
    E -- có --> F["score = w1·distToDock + w2·blocking<br/>+ w3·FEFO(z) + w4·fitPenalty"]
    F --> D
    D --> G["sắp xếp ứng viên theo score tăng dần"]
    G --> H([Trả recommendedBin = score nhỏ nhất<br/>+ danh sách xếp hạng])
```

## 12. ERD đầy đủ (mọi cột)

Phiên bản chi tiết hơn §2 — đủ cột + khóa, khớp [data-model.md](./data-model.md) và Flyway `V1`.

```mermaid
erDiagram
    sku ||--o{ lot : sku_id
    lot ||--|| placement : "lot_id (UNIQUE)"
    lot ||--o{ movement : lot_id
    location ||--o{ placement : bin_id
    location ||--o{ movement : "from_bin / to_bin"

    sku {
        bigint id PK
        varchar code UK
        varchar name
        numeric w
        numeric d
        numeric h
        numeric weight
        varchar handling "FIFO|FEFO"
    }
    location {
        bigint id PK
        varchar zone
        varchar aisle
        varchar rack
        varchar level
        varchar bin
        numeric x
        numeric y
        numeric z
        numeric w
        numeric d
        numeric h
        varchar lane_id "INDEX"
        varchar access_face "N|S|E|W|TOP"
    }
    lot {
        bigint id PK
        bigint sku_id FK
        numeric w
        numeric d
        numeric h
        numeric weight
        date expiry
        timestamptz predicted_retrieval_at
    }
    placement {
        bigint id PK
        bigint lot_id FK_UK
        bigint bin_id FK
        numeric x
        numeric y
        numeric z
    }
    movement {
        bigint id PK
        bigint lot_id FK
        varchar type "5 loại"
        bigint from_bin FK
        bigint to_bin FK
        timestamptz ts
        varchar actor
        varchar scan_ref
    }
```

---

### Ghi chú về file Astah
`diagrams/stockpile-3d.asta` chứa 4 package: `domain`, `service`, `erd`,
`architecture`. Khi mở bằng Astah bạn sẽ thấy một số class "kiểu dữ liệu"
(`Long`, `String`, `bigint`…) được sinh tự động do công cụ — bỏ qua khi vẽ
diagram. Quan hệ FK hiển thị dưới dạng association giữa các class.
