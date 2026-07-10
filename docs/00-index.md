# 📁 Bộ tài liệu dự án Stockpile-3D — Mục lục

> **Stockpile-3D** — Hệ thống quản lý kho 3D thông minh: visualization 3D (React Three Fiber) + engine tối ưu vận hành (Container Relocation Problem, Storage Location Assignment Problem). Bộ tài liệu này là nguồn sự thật duy nhất về định hướng sản phẩm, kiến trúc và quy trình phát triển.

---

## Danh sách tài liệu — **đánh số theo thứ tự nên đọc**

> Cột **#** = thứ tự đề xuất đọc để **hiểu dự án từ đầu**. Người mới cứ đọc 1 → 11 lần lượt. (Lưu ý: 3 file đầu mang số `01/02/03` trùng với *tên file* — vẫn giữ để khớp tên trên ổ đĩa.)

### A. Hiểu dự án (đọc trước — nên theo thứ tự này)

| # | Tài liệu | Nội dung | Khi nào đọc |
|---|---|---|---|
| 1 | [Tổng quan & Thiết kế](./01-overview.md) *(file `01`)* | Vấn đề, định vị, data model, NFR, kiến trúc, thuật toán + Big-O, roadmap | Đọc **đầu tiên** — hiểu *cái gì* và *vì sao* |
| 2 | [Nghiệp vụ kho](./business.md) | Quy trình WMS (5 loại movement), vai trò người dùng, vòng đời lô, FIFO/FEFO, ground-truth | Hiểu *nghiệp vụ* hệ thống hỗ trợ |
| 3 | [Data model](./data-model.md) | 5 bảng: từng cột/ràng buộc/index + **lý do thiết kế** + vòng đời dữ liệu | Hiểu dữ liệu được lưu thế nào |
| 4 | [Kiến trúc hệ thống](./architecture.md) | 3 tầng, luồng dữ liệu (write/read/decision), invariant, NFR, **API reference**, deploy | Hiểu hệ thống ráp nối ra sao |
| 5 | [Đặc tả thuật toán](./algorithm-spec.md) | CRP + SLAP: bài toán, NP-hard, pseudocode, **worked example từng bước**, Big-O, trade-off | Hiểu sâu 2 bộ não (Relocation/Putaway) |
| 6 | [Sơ đồ thiết kế](./diagrams.md) | 18 sơ đồ Mermaid bao quát mọi nghiệp vụ: class/ERD (V1–V6) · component · use case · state (lô/đơn/transfer) · activity · sequence (putaway/CRP/picking/transfer/scan/realtime) · flowchart CRP+SLAP | Nhìn trực quan mọi thứ ở trên |

### B. Tra cứu khi cần (không cần đọc tuần tự)

| # | Tài liệu | Nội dung | Khi nào đọc |
|---|---|---|---|
| 7 | [Từ điển thuật ngữ](./glossary.md) | Giải thích ngắn tiếng Việt các thuật ngữ kỹ thuật, thư viện, pattern | Bất cứ lúc nào gặp từ chưa rõ |
| 8 | [Lộ trình học](./learning-path.md) | Bài luyện thuật toán (LeetCode) + tài liệu map vào dự án | Khi muốn học thêm để hiểu sâu |
| 9 | [Đề xuất thiết kế hệ thống](./system-design-proposal.md) | Công nghệ + mẫu thiết kế giúp mạnh/scale/tin cậy hơn (CQRS, Redis, optimistic lock, Kafka, Hexagonal...) — kèm ưu tiên Now/Next/Later | Khi nghĩ về nâng cấp / scale / production |
| 10 | [Thiết lập kho](./warehouse-setup.md) | Các cách tạo dữ liệu vị trí (generator theo lưới / import CSV-Excel / editor 3D) — kèm trade-off + ưu tiên Now/Next/Later | Khi cần dựng dữ liệu kho để chạy/thử |
| 11 | [Dev Log](./dev-log.md) | Nhật ký vấn đề + nguyên nhân + cách giải | Xem các bug đã gặp & cách xử lý |

### C. Quy trình phát triển (đọc khi bắt tay code/đóng góp)

| # | Tài liệu | Nội dung | Khi nào đọc |
|---|---|---|---|
| 12 | [Git Workflow](./02-git-workflow.md) *(file `02`)* | Branching, Conventional Commits, cấu trúc repo, README checklist | Trước khi commit dòng code đầu tiên |
| 13 | [Quy trình tài liệu hóa](./03-documentation.md) *(file `03`)* | Chuẩn ADR (Nygard), Keep a Changelog, Dev Log, Algorithm Spec | Khi cần ghi lại quyết định/thay đổi |

> **Kế hoạch & tiến độ:** [commit-plan.md](./commit-plan.md) (task theo giai đoạn) · [CHANGELOG](../CHANGELOG.md) (đã làm gì). **Quyết định lớn:** [adr/](./adr/).

---

## Cách dùng theo tình huống

- **Người mới muốn hiểu dự án:** đọc lần lượt **1 → 6** (nhóm A). Mỗi doc kỹ thuật có mục "📖 Nói nôm na" ở đầu — đọc mục đó trước là nắm ý chính.
- **Bắt tay viết code:** đọc thêm **11 → 12** (nhóm C) để theo đúng quy trình git/tài liệu.
- **Nghĩ về nâng cấp/scale:** đọc **9** ([đề xuất thiết kế](./system-design-proposal.md)) sau khi đã nắm hiện trạng (1 → 6).
- **Chuẩn bị phỏng vấn:** doc 1 (vấn đề + thiết kế) + doc 5 (thuật toán) + [adr/](./adr/) (vì sao chọn cách này) — câu hỏi gần như chắc chắn gặp.

---

## Cấu trúc repo chuẩn (nguồn sự thật duy nhất)

> Cả file 02 tham chiếu về đúng cấu trúc này. Không định nghĩa cấu trúc ở nơi khác để tránh lệch nhau.

```
stockpile-3d/
├── README.md                 # Cổng vào: overview + cách chạy + demo
├── CHANGELOG.md              # Keep a Changelog
├── .gitignore
├── docker-compose.yml        # chạy cả hệ thống bằng 1 lệnh
├── docs/
│   ├── 00-index.md           # Mục lục (file này)
│   ├── 01-overview.md        # 1. Tổng quan & thiết kế
│   ├── business.md           # 2. Nghiệp vụ kho
│   ├── data-model.md         # 3. Data model (5 bảng)
│   ├── architecture.md       # 4. Kiến trúc hệ thống
│   ├── algorithm-spec.md     # 5. Đặc tả thuật toán CRP + SLAP
│   ├── diagrams.md           # 6. Sơ đồ (Mermaid + Astah)
│   ├── glossary.md           # 7. Từ điển thuật ngữ
│   ├── learning-path.md      # 8. Lộ trình học
│   ├── system-design-proposal.md  # 9. Đề xuất thiết kế (scale/production)
│   ├── dev-log.md            # 10. Dev Log
│   ├── 02-git-workflow.md    # 10. Git workflow
│   ├── 03-documentation.md   # 11. Quy trình tài liệu hóa
│   ├── commit-plan.md        # Kế hoạch task theo giai đoạn
│   ├── diagrams/             # file Astah (.asta)
│   └── adr/                  # 0001 greedy CRP · 0002 Spring Boot · 0003 ledger projection · 0004 client web→PWA→Capacitor · 0005 realtime STOMP · 0006 picking · 0007 barcode scan · 0008 what-if · 0009 multi-warehouse
├── src/
│   ├── backend/              # Java + Spring Boot
│   ├── frontend/             # Next.js + React Three Fiber
│   └── shared/
└── tests/
```

> Các file `.md` đã được đổi tên sang tiếng Anh và chuyển vào `docs/` để đồng bộ với code. Nội dung giữ tiếng Việt là chấp nhận được — nhiều repo VN làm vậy; quan trọng là nhất quán.

---

## Việc tiếp theo (theo thứ tự ưu tiên)

1. Khởi tạo repo `stockpile-3d` (remote `github.com/<username>/stockpile-3d`), `.gitignore`, cấu trúc thư mục như trên; tạo database Postgres tên `stockpile_3d`.
2. Viết `README.md` theo checklist trong 02 (phần "cách chạy" để trống tới khi có code).
3. Code MVP Giai đoạn 1 (roadmap trong 01) trên nhánh `feature/`.
4. Viết ADR đầu tiên ngay khi có quyết định kỹ thuật thật (ví dụ: cách biểu diễn toạ độ/quan hệ blocking — xem ADR-0001 mẫu trong 03).

---

*Tài liệu sống — cập nhật liên tục theo tiến độ, đúng tinh thần "tài liệu hóa nhẹ và liên tục" ở file 03.*
