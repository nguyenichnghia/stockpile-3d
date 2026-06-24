# Lộ trình học — hiểu sâu Stockpile-3D

> Map từng phần của dự án sang **bài luyện thuật toán** + **tài liệu** để học có mục tiêu. ⭐ = nên học trước. Tick `[x]` khi xong để theo dõi tiến độ. Thuật ngữ giải thích trong [glossary.md](./glossary.md).

**Mẹo học:** mỗi khi giải xong một bài, tự hỏi *"chỗ nào trong dự án dùng ý này?"* rồi mở đúng file code đọc lại. Liên kết bài tập ↔ code thật giúp nhớ gấp đôi.

---

## 1. Thuật toán — phần lõi giá trị (CRP + SLAP)

### ⭐ Hình học / interval overlap — chính là `BlockingGraph.overlaps()`
Code blocking dùng overlap khoảng (`min1 < max2 && min2 < max1`) trên từng trục.
- [ ] [LC 836 Rectangle Overlap](https://leetcode.com/problems/rectangle-overlap/) — *giống hệt* kiểm overlap (x,y); mở lên 3D là đúng cái dự án làm
- [ ] [LC 56 Merge Intervals](https://leetcode.com/problems/merge-intervals/)
- [ ] [LC 252 / 253 Meeting Rooms I & II](https://leetcode.com/problems/meeting-rooms-ii/)
- [ ] [LC 57 Insert Interval](https://leetcode.com/problems/insert-interval/)
- 🔗 Đọc lại: [BlockingGraph.java](../src/backend/src/main/java/com/stockpile/relocation/service/BlockingGraph.java)

### ⭐ Đồ thị (graph) — nền của blocking graph (CRP)
- [ ] [LC 207 Course Schedule](https://leetcode.com/problems/course-schedule/) — topological sort (sắp xếp tô-pô), *gần như* "thứ tự dời lô"
- [ ] [LC 210 Course Schedule II](https://leetcode.com/problems/course-schedule-ii/)
- [ ] [LC 200 Number of Islands](https://leetcode.com/problems/number-of-islands/) — DFS/BFS (duyệt sâu/rộng)
- [ ] [LC 802 Find Eventual Safe States](https://leetcode.com/problems/find-eventual-safe-states/) — cycle detection (blocking graph phải là DAG)

### ⭐ Greedy (tham lam) — cách giải v1 của CRP & SLAP (xem ADR-0001)
- [ ] [LC 455 Assign Cookies](https://leetcode.com/problems/assign-cookies/) — tư duy greedy cơ bản
- [ ] [LC 435 Non-overlapping Intervals](https://leetcode.com/problems/non-overlapping-intervals/)
- [ ] [LC 621 Task Scheduler](https://leetcode.com/problems/task-scheduler/)
- 💡 Hiểu *khi nào greedy KHÔNG tối ưu* = trả lời được "vì sao chọn greedy thay vì branch-and-bound" ([ADR-0001](./adr/0001-greedy-crp-heuristic.md))

### Heap / Priority Queue — cho `pickBlocker` & SLAP scoring
- [ ] [LC 973 K Closest Points to Origin](https://leetcode.com/problems/k-closest-points-to-origin/) — *chính là* SLAP chọn vị trí gần dock nhất
- [ ] [LC 215 Kth Largest Element](https://leetcode.com/problems/kth-largest-element-in-an-array/)

### Nâng cao (để sau — hướng cải thiện CRP)
- [ ] [LC 51 N-Queens](https://leetcode.com/problems/n-queens/) — backtracking, nền cho branch & bound
- [ ] Đọc lý thuyết **Branch & Bound** (nhánh-cận) + **Beam Search** (tìm kiếm chùm) — không có trên LeetCode

---

## 2. Nghiệp vụ kho (WMS) — phần làm dự án khác biệt

- [ ] **Event Sourcing** (lưu trạng thái bằng chuỗi sự kiện) — *chính là* ledger + projection ([ADR-0003](./adr/0003-ledger-projection.md)): [Martin Fowler — Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html) ⭐
- [ ] **CQRS** (tách lệnh ghi / truy vấn đọc): [Martin Fowler — CQRS](https://martinfowler.com/bliki/CQRS.html)
- [ ] **CRP học thuật:** Caserta, Schwarze, Voß — *"Container Rehandling at Maritime Container Terminals"* (Google Scholar) — nguồn của ADR-0001
- [ ] **SLAP học thuật:** search *"Storage Location Assignment Problem survey"*
- [ ] Khái niệm WMS: FIFO/FEFO, block stacking, putaway/picking/replenishment — xem [glossary.md](./glossary.md)

---

## 3. Backend — Java + Spring Boot

- [ ] ⭐ [Spring Guides](https://spring.io/guides) — "Building a RESTful Web Service" + "Accessing Data with JPA"
- [ ] [Vlad Mihalcea blog](https://vladmihalcea.com/) — JPA/Hibernate chuyên sâu (`@ManyToOne`, lazy/eager, N+1 problem)
- [ ] [Flyway docs](https://documentation.red-gate.com/fd) — versioned migration (di trú schema theo phiên bản)
- [ ] [Testcontainers docs](https://testcontainers.com/) — đã dùng trong dự án, đọc hiểu sâu
- 🔗 Đọc lại: [PlacementProjectionService.java](../src/backend/src/main/java/com/stockpile/inventory/service/PlacementProjectionService.java)

---

## 4. Database — PostgreSQL + SQL

- [ ] ⭐ [SQLBolt](https://sqlbolt.com/) — SQL interactive, nhanh
- [ ] ⭐ [LeetCode Top SQL 50](https://leetcode.com/studyplan/top-sql-50/) — JOIN, GROUP BY, subquery, window function
- [ ] `EXPLAIN ANALYZE` + index — liên quan index theo lane (`location(lane_id)`) trong [V1__core_schema.sql](../src/backend/src/main/resources/db/migration/V1__core_schema.sql)
- [ ] [Use The Index, Luke](https://use-the-index-luke.com/) — kinh điển về indexing, miễn phí
- 💡 ADR-0002 nói sẽ dùng **native query** (SQL tay) cho blocking nặng → SQL thuần là kỹ năng bắt buộc

---

## 5. Docker & Container

- [ ] ⭐ [Docker Get Started](https://docs.docker.com/get-started/) — image (khuôn) vs container (thực thể chạy), Dockerfile, build/run/ps/logs
- [ ] ⭐ [Docker Compose](https://docs.docker.com/compose/) — chạy nhiều container bằng 1 lệnh; xem [docker-compose.yml](../docker-compose.yml) của dự án
- [ ] [Docker Desktop WSL2 backend](https://docs.docker.com/desktop/wsl/) — vì sao Docker chạy Linux engine trong Windows
- 💡 Testcontainers *spawn* Postgres container mỗi lần test → hiểu Docker = hiểu vì sao test cần Docker chạy

---

## 6. Git & quy trình

- [ ] ⭐ [Learn Git Branching](https://learngitbranching.js.org/) — game tương tác: branch/merge/rebase
- [ ] [Conventional Commits](https://www.conventionalcommits.org/) + [Keep a Changelog](https://keepachangelog.com/) — dự án đang dùng
- [ ] [GitHub Actions](https://docs.github.com/actions) — CI/CD: tự chạy `mvnw test` mỗi lần push (bước tiếp theo của dự án)

---

## 7. API & Realtime (Phase 3 sắp tới)

- [ ] [restfulapi.net](https://restfulapi.net/) — method + status code (200/201/404/400)
- [ ] [Swagger/OpenAPI docs](https://swagger.io/docs/) — dự án có springdoc
- [ ] ⭐ **WebSocket** ([MDN](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API) + [Spring guide](https://spring.io/guides/gs/messaging-stomp-websocket/)) — **Phase 3**: đẩy delta realtime tới 3D scene

---

## 8. Frontend — 3D

- [ ] ⭐ [Three.js Journey](https://threejs-journey.com) — khóa 3D web tốt nhất (có InstancedMesh + performance)
- [ ] [React Three Fiber docs](https://r3f.docs.pmnd.rs/) — "Your first scene" + "Performance/Instances"
- [ ] [Three.js Manual](https://threejs.org/manual/) — camera, geometry, hệ trục Y-up
- 🔗 Đọc lại: [Warehouse3D.tsx](../src/frontend/src/components/Warehouse3D.tsx)

---

## 9. Mở rộng (khi đi làm / dự án lớn)

| Công nghệ | Khi nào cần |
|---|---|
| Redis (cache/lock) | Optimistic lock trên `bin` (Phase 3) |
| Message queue (Kafka/RabbitMQ) | Ledger stream tới nhiều consumer |
| Kubernetes | Deploy nhiều service (Phase 4) |
| Nginx / reverse proxy | Frontend + backend chung domain (tránh CORS) |
| PostGIS | Spatial index toàn cục (ADR-0002 đã quyết *chưa* dùng) |

---

## Lộ trình gợi ý theo tuần (nếu cần thứ tự)

| Tuần | Trọng tâm | Lý do |
|---|---|---|
| 1–2 | Interval/Rectangle Overlap (§1 hình học) | Hiểu ngay `BlockingGraph` của chính mình |
| 2–3 | Graph (topo sort, DFS) + Greedy (§1) | Hiểu CRP — phần giá trị nhất |
| 3–4 | Event Sourcing + Spring JPA (§2, §3) | Hiểu kiến trúc ledger/projection |
| 4–5 | SQL (§4) + Docker/Compose (§5) | Kỹ năng nền backend + deploy |
| 5–6 | Git nâng cao (§6) + WebSocket (§7) | Quy trình + chuẩn bị Phase 3 |
| 6+ | Heap + Three.js (§1, §8) | SLAP scoring + 3D viewer |

> **Ưu tiên nếu ít thời gian** (mục tiêu hiểu dự án + intern backend): SQL → Docker/Compose → Git nâng cao → WebSocket. 3D để cuối (lớp trình bày, không phải lõi giá trị).
