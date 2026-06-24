# 📁 Bộ tài liệu dự án Stockpile-3D — Mục lục

> **Stockpile-3D** — Hệ thống quản lý kho 3D thông minh: visualization 3D (React Three Fiber) + engine tối ưu vận hành (Container Relocation Problem, Storage Location Assignment Problem). Bộ tài liệu này là nguồn sự thật duy nhất về định hướng sản phẩm, kiến trúc và quy trình phát triển.

---

## Danh sách tài liệu

| # | Tài liệu | Nội dung | Khi nào đọc |
|---|---|---|---|
| 01 | [Tổng quan & Thiết kế](./01-overview.md) | Vấn đề, định vị, **data model**, **NFR**, kiến trúc, **formulation thuật toán + Big-O**, roadmap, rủi ro | Hiểu *cái gì* và *vì sao* |
| 02 | [Git Workflow](./02-git-workflow.md) | Branching, Conventional Commits, cấu trúc repo, README checklist | Trước khi commit dòng code đầu tiên |
| 03 | [Quy trình tài liệu hóa](./03-documentation.md) | Chuẩn ADR (Nygard), Keep a Changelog, Dev Log, Algorithm Spec | Khi cần ghi lại quyết định/thay đổi |
| — | [Sơ đồ thiết kế](./diagrams.md) | Class (domain/service), ERD, component, use case, activity, sequence (Mermaid) + mô hình Astah `diagrams/*.asta` | Khi cần nhìn trực quan data model & kiến trúc |
| — | [Từ điển thuật ngữ](./glossary.md) | Giải thích ngắn tiếng Việt các thuật ngữ kỹ thuật, thư viện, pattern | Khi gặp thuật ngữ chưa rõ |
| — | [Đặc tả thuật toán](./algorithm-spec.md) | CRP + SLAP: bài toán, NP-hard, pseudocode, Big-O, trade-off, test case | Khi cần hiểu sâu Relocation/Putaway Engine |
| — | [Dev Log](./dev-log.md) | Nhật ký vấn đề + nguyên nhân + cách giải | Khi muốn xem các bug đã gặp & cách xử lý |
| — | [Lộ trình học](./learning-path.md) | Bài luyện thuật toán (LeetCode) + tài liệu nghiệp vụ/công nghệ map vào dự án | Khi muốn học thêm để hiểu dự án |

> **Đã có:** `docs/algorithm-spec.md` (CRP + SLAP). ADR cụ thể trong `docs/adr/`.

---

## Cách dùng theo tình huống

- **Mới bắt đầu code:** đọc 01 → 02 → 03 theo thứ tự.
- **Đã có code, chỉ chuẩn hóa quy trình:** chỉ cần 02 + 03, áp dụng trực tiếp vào repo.
- **Chuẩn bị phỏng vấn:** 01 để trả lời "dự án giải quyết vấn đề gì + thiết kế ra sao"; ADR (mẫu ở 03) để trả lời "vì sao chọn cách này mà không phải cách khác" — câu hỏi gần như chắc chắn gặp.

---

## Cấu trúc repo chuẩn (nguồn sự thật duy nhất)

> Cả file 02 tham chiếu về đúng cấu trúc này. Không định nghĩa cấu trúc ở nơi khác để tránh lệch nhau.

```
stockpile-3d/
├── README.md                 # Cổng vào: overview + cách chạy + demo
├── CHANGELOG.md              # Keep a Changelog
├── .gitignore
├── docs/
│   ├── 00-index.md           # Mục lục (file này)
│   ├── 01-overview.md        # Tổng quan & thiết kế
│   ├── 02-git-workflow.md    # Git workflow
│   ├── 03-documentation.md   # Quy trình tài liệu hóa
│   ├── algorithm-spec.md     # Viết khi xong Relocation Engine
│   ├── api-spec.md           # Tùy chọn, viết khi API ổn định
│   └── adr/
│       ├── 0000-template.md
│       └── 0001-greedy-crp-heuristic.md
├── src/
│   ├── backend/
│   ├── frontend/
│   └── shared/               # type/contract dùng chung BE-FE
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
