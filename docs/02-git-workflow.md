# Git Workflow — Stockpile-3D (dự án cá nhân)

## 1. Vì sao dự án solo vẫn cần workflow nghiêm túc

Git history là **bằng chứng cách bạn làm việc** — nhà tuyển dụng kỹ thuật thường xem commit log trước khi đọc code. History lộn xộn ("fix bug", "update", "asdasd") tạo cảm giác làm ẩu dù code tốt.

**Nguyên tắc:** làm như đang trong một team thật, dù chỉ có một mình — đồng thời là cách luyện thói quen trước khi vào công ty.

---

## 2. Branching strategy — đơn giản, đủ dùng

Không cần Git Flow đầy đủ (develop/release/hotfix quá phức tạp cho 1 người). Dùng mô hình rút gọn:

```bash
# Khởi tạo / clone repo
git clone git@github.com:<username>/stockpile-3d.git
cd stockpile-3d
# hoặc tạo mới:  git init  →  git remote add origin git@github.com:<username>/stockpile-3d.git
```

```
main              ← luôn build pass, ổn định
 ├── feature/xxx   ← mỗi tính năng 1 nhánh
 └── fix/xxx       ← mỗi bug fix 1 nhánh
```

- `main` không bao giờ nhận code đang dở.
- Mỗi tính năng/bug → nhánh riêng, merge qua Pull Request. Dù tự review, việc tạo PR + viết mô tả vẫn để lại lịch sử thay đổi có giá trị.

```bash
git checkout -b feature/relocation-engine
# ... code ...
git commit -m "feat: implement greedy CRP heuristic for block relocation"
git push origin feature/relocation-engine
# Mở PR trên GitHub → tự review → merge vào main (squash hoặc merge commit, chọn 1 và nhất quán)
```

---

## 3. Commit message — Conventional Commits

Chuẩn dùng rộng rãi trong ngành, giúp git log đọc như changelog:

```
<type>: <mô tả ngắn, động từ, không viết hoa đầu>

feat:     add ABC analysis for putaway suggestion
fix:      correct bounding box overlap in blocking graph
docs:     update README with CRP explanation
refactor: extract spatial index logic into a service
test:     add unit tests for greedy relocation heuristic
chore:    update dependencies
```

| Type | Khi nào |
|---|---|
| `feat` | Tính năng mới |
| `fix` | Sửa lỗi |
| `docs` | Chỉ thay đổi tài liệu |
| `refactor` | Tái cấu trúc, không đổi hành vi |
| `test` | Thêm/sửa test |
| `chore` | Việc lặt vặt (deps, config) |

**Quy tắc:** commit nhỏ, mỗi commit đúng 1 việc — dễ review, dễ revert.

---

## 4. Cấu trúc thư mục

> **Nguồn sự thật là [`00-index.md`](./00-index.md).** File này chỉ trích phần liên quan để khỏi lệch.

```
stockpile-3d/
├── README.md          # cổng vào: overview + cách chạy + demo
├── CHANGELOG.md        # Keep a Changelog
├── .gitignore
├── docs/               # chi tiết kỹ thuật (xem INDEX để biết đủ)
│   ├── 01-overview.md
│   ├── 02-git-workflow.md
│   ├── 03-documentation.md
│   ├── algorithm-spec.md
│   └── adr/
├── src/
│   ├── backend/
│   ├── frontend/
│   └── shared/
└── tests/
```

README chỉ là "cổng vào"; chi tiết kỹ thuật để trong `docs/`, không nhồi hết vào một README dài.

---

## 5. .gitignore — chi tiết nhỏ nhưng bị soi

```
node_modules/
.env
.env.local
dist/
build/
*.log
.DS_Store
```

Push `node_modules/` hay file `.env` chứa secret lên GitHub là lỗi cơ bản nhưng vẫn thường gặp — ấn tượng xấu ngay từ lần xem repo đầu tiên.

Mẫu `.env.local` (không commit) — lưu ý tên database `stockpile_3d`:

```
DATABASE_URL="postgresql://postgres:<password>@localhost:5432/stockpile_3d"
# Supabase (nếu dùng): SUPABASE_URL, SUPABASE_ANON_KEY...
```

---

## 6. Tagging version

Hoàn thành 1 giai đoạn → tạo tag (đồng bộ với CHANGELOG):

```bash
git tag -a v0.1.0 -m "MVP: 3D viewer + basic inventory CRUD"
git push origin v0.1.0
```

Giúp người xem repo thấy mốc tiến trình rõ ràng, không chỉ là một đống commit.

---

## 7. README — checklist tối thiểu

- [ ] Dự án giải quyết vấn đề gì (1–2 đoạn, không lan man)
- [ ] Kiến trúc tổng thể (kèm diagram đơn giản)
- [ ] Công nghệ **và vì sao chọn** (thể hiện tư duy, không chỉ liệt kê)
- [ ] Cách chạy (setup/install/run) — **phải chạy được thật**
- [ ] Điểm kỹ thuật nổi bật (giải thích ngắn CRP/SLAP) — phần "bán" giá trị
- [ ] Demo (GIF/video/link) nếu có

---

*Tập trung thực hành tối thiểu cho dự án solo — không áp dụng quy trình team lớn (CI/CD phức tạp, nhiều môi trường) vì chưa cần ở quy mô này.*
