# Kế hoạch commit theo giai đoạn — Stockpile-3D

> Bám theo roadmap §9 và NFR §7 của [01-overview.md](./01-overview.md), quy ước git ở [02-git-workflow.md](./02-git-workflow.md) và quy trình tài liệu ở [03-documentation.md](./03-documentation.md). Tên commit/nhánh tiếng Anh; nội dung tài liệu tiếng Việt.

## Quy ước nền

- **Commit khởi tạo nằm trên `main`** (init repo, cấu trúc thư mục, `.gitignore`, `CHANGELOG`, `README`). Sau mốc đó, `main` luôn build pass ([02 §2](./02-git-workflow.md)).
- **Mọi thay đổi sau đó đi qua nhánh `feature/` (hoặc `fix/`) → Pull Request → squash-merge** vào `main`. Một nhánh = một tính năng/fix; commit nhỏ, mỗi commit đúng một việc.
- **Conventional Commits** ([02 §3](./02-git-workflow.md)): `feat`/`fix`/`docs`/`refactor`/`test`/`chore`, mô tả ngắn, động từ, không viết hoa đầu.
- **Mỗi PR lớn kèm cập nhật tài liệu** tương ứng (CHANGELOG luôn; ADR khi có quyết định lớn; Dev Log khi gặp vấn đề đáng nhớ; Algorithm Spec khi xong module thuật toán) — theo nhịp ở [03 §7](./03-documentation.md).
- **Tag mốc đồng bộ với CHANGELOG** ([03 §4](./03-documentation.md)): gắn tag ngay khi gom xong section version từ `[Unreleased]`.

## Bảng kế hoạch

| Giai đoạn | Nhánh `feature/` | Các commit chính (Conventional, EN) | Tài liệu kèm | Tag mốc |
|---|---|---|---|---|
| **0 — Khởi tạo** *(trên `main`, không qua PR)* | — | `chore: init repo structure and docs`<br>`chore: add gitignore and changelog`<br>`docs: add README` | README, CHANGELOG | — |
| **1 — MVP** | `feature/data-model-ledger`<br>`feature/3d-viewer`<br>`feature/inventory-crud` | `feat: add location/sku/lot/placement/movement schema`<br>`feat: rebuild placement projection from ledger`<br>`feat: render warehouse with InstancedMesh`<br>`feat: add inventory CRUD endpoints` | ADR (ledger append-only là nguồn sự thật; blocking cục bộ theo lane, không PostGIS), CHANGELOG | `v0.1.0` |
| **2 — Lõi thuật toán** | `feature/relocation-engine`<br>`feature/putaway-engine` | `feat: build per-lane blocking graph`<br>`feat: implement greedy CRP heuristic`<br>`feat: implement SLAP greedy scoring`<br>`test: add relocation/putaway unit tests`<br>`perf: optimize blocking graph to O(n log n)` | **ADR-0001 (greedy CRP)**, `docs/algorithm-spec.md`, Dev Log (edge case `>` vs `≥` khi check "nằm trên"), CHANGELOG | `v0.2.0` |
| **3 — Thời gian thực** | `feature/websocket-sync`<br>`feature/barcode-scan`<br>`feature/zone-picking` | `feat: push scene deltas over WebSocket`<br>`feat: add barcode scan touchpoints`<br>`feat: add zone/wave picking (FIFO/FEFO)` | ADR (nếu chốt cơ chế sync/optimistic lock trên `bin`), CHANGELOG | `v0.3.0` |
| **4 — Phân tích & mở rộng** | `feature/heatmap-reports`<br>`feature/what-if`<br>`feature/multi-warehouse` | `feat: add density heatmap`<br>`feat: add what-if layout simulation`<br>`feat: add reporting dashboard` | ADR (xem lại spatial index toàn cục khi multi-warehouse), CHANGELOG | `v1.0.0` |

> Tên commit là gợi ý mẫu bám đúng module trong [01 §8](./01-overview.md) và ví dụ Conventional ở [02 §3](./02-git-workflow.md); sẽ tinh chỉnh khi code thật. Mục tiêu định lượng để kiểm chứng từng giai đoạn (CRP < 500 ms/lane, render ~60 fps tới ~50k instance, sync < 1 s) nằm ở [NFR §7](./01-overview.md).

## Chốt tính năng

Khi xong một nhánh, dùng slash command **`/ship`** ([.claude/commands/ship.md](../.claude/commands/ship.md)) để chạy quy trình hoàn tất: build + test → đề xuất commit nhỏ → cập nhật CHANGELOG → nháp ADR nếu cần → tóm tắt PR. `/ship` không tự merge/push.
