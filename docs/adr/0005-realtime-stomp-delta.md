# ADR-0005: Realtime đẩy delta placement qua STOMP/WebSocket

## Trạng thái
Accepted — 2026-07-02

## Bối cảnh
3D scene hiện là ảnh chụp: `page.tsx` (SSR `force-dynamic`) fetch `locations` +
`placements` **một lần**; khi có movement (putaway/relocate/pick/outbound), scene
không cập nhật cho tới khi tải lại trang. ADR-0002 đã hứa "Spring WebSocket đẩy
delta tới 3D scene" và [system-design §C1](../system-design-proposal.md) đặt đây là
việc Phase 3/4; package `com.stockpile.realtime` tồn tại nhưng rỗng. NFR: scan →
scene < 1s. Cần chọn **giao thức**, **hình dạng delta**, và **cách phát sự kiện**
mà không phá vỡ invariant "ledger là nguồn sự thật duy nhất" (ADR-0003).

## Quyết định
- **Giao thức: Spring WebSocket + STOMP**, simple broker in-memory. Endpoint `/ws`,
  topic `/topic/lane/{laneId}`. Lý do: `spring-boot-starter-websocket` **đã có sẵn**
  trong pom; lọc theo lane/zone là thế mạnh bản chất của STOMP (subscribe/unsubscribe
  theo topic); và ADR-0002 vốn đã nêu WebSocket → quyết định này **giữ đúng** ADR-0002,
  **không supersede**. (SSE được cân nhắc nhưng yếu hơn ở lọc topic động.)
- **Đẩy delta, không đẩy snapshot/tín hiệu refetch.** Hợp đồng client là record
  `PlacementDelta { kind (UPSERT|REMOVE), lotId, binId, x, y, z, ts }`. Frontend gộp
  theo **`lotId`** (một lô có ≤1 placement) — khóa ổn định qua add→move→remove.
- **Định tuyến theo lane:**
  - UPSERT (INBOUND có bin / PUTAWAY): gửi tới lane đích.
  - REMOVE (PICK/OUTBOUND): gửi tới lane gốc (placement đã bị xóa → delta dựng từ `lotId`).
  - RELOCATE cùng lane: một UPSERT tới lane đó.
  - RELOCATE **khác lane**: UPSERT tới lane đích **và** REMOVE tới lane gốc (hai delta
    khác nhau — không gửi cùng một UPSERT tới hai nơi, nếu không lane gốc vẫn giữ lô).
- **Phát sự kiện từ đường ghi duy nhất, tách rời transport:** `MovementService.record`
  publish một domain event scalar (`MovementRecordedEvent`) qua `ApplicationEventPublisher`;
  một listener trong `realtime` (`PlacementBroadcaster`) dịch sang `PlacementDelta` và
  đẩy qua `SimpMessagingTemplate`. `inventory` **không** import `realtime.dto` (phân tầng
  sạch, đúng chiều phụ thuộc). Trùng khớp system-design §C2 (Domain Events).
- **An toàn transaction:** listener dùng `@TransactionalEventListener(phase = AFTER_COMMIT)`
  → chỉ broadcast sau khi movement + projection đã commit; movement bị rollback **không**
  phát delta "ma".

## Hệ quả
Tích cực:
- Scene "sống": mọi tính năng hiện có (locate, heatmap) phản ánh thay đổi tức thì, không
  reload. Giữ đúng NFR < 1s.
- Realtime là **read-side consumer**, không phải đường ghi thứ hai — ledger vẫn là nguồn
  sự thật (ADR-0003 nguyên vẹn). MovementService không biết gì về STOMP.
- Lọc theo lane có sẵn từ v1; client chỉ nhận delta vùng đang tải.

Tiêu cực / đánh đổi:
- Simple broker in-memory chạy **một node** (không scale ngang, không bền message) — chấp
  nhận được ở quy mô portfolio. Đường nâng cấp: external broker relay (RabbitMQ/ActiveMQ)
  khi multi-instance.
- Frontend giữ `placements` ở client state để mutate theo delta → bộ đếm ở header (`page.tsx`,
  SSR) không live; lift state để đồng bộ sau (defer).
- v1 subscribe **mọi lane đã tải** (đơn giản, đúng). Lọc theo lane *đang nhìn* (frustum) là
  tối ưu sau — hợp đồng topic đã sẵn, không cần đổi backend.
