# Quy trình tài liệu hóa & ghi log — Stockpile-3D (dự án cá nhân)

## 1. Vì sao tài liệu hóa quan trọng với dự án solo

Với dự án solo nhắm mục tiêu xin intern, tài liệu có 2 vai trò:
1. **Bằng chứng tư duy** — nhà tuyển dụng không đọc hết code, nhưng đọc tài liệu để hiểu bạn quyết định gì, vì sao.
2. **Công cụ tự đối thoại** — viết ra buộc bạn làm rõ suy nghĩ; thường lúc viết mới phát hiện lỗ hổng thiết kế của chính mình.

---

## 2. Phân loại tài liệu — đúng loại đúng chỗ

| Loại | Mục đích | Khi nào viết |
|---|---|---|
| **README.md** | Cổng vào: overview + cách chạy | Từ đầu, update liên tục |
| **ADR** | Ghi quyết định kỹ thuật + hệ quả | Mỗi quyết định lớn (DB, thuật toán...) |
| **CHANGELOG.md** | Lịch sử thay đổi theo version | Mỗi khi merge tính năng lớn |
| **Algorithm Spec** | Đặc tả chi tiết CRP/putaway | Khi xong module thuật toán |
| **Dev Log** | Nhật ký vấn đề + cách giải | Cuối buổi code, nếu có gì đáng nhớ |

---

## 3. ADR — chuẩn Michael Nygard

ADR ghi **quyết định + hệ quả**, không chỉ "đã làm gì". Quy ước:

- **Đánh số liên tục, immutable:** `0001`, `0002`... Đã `Accepted` thì **không sửa nội dung** — nếu đổi ý, viết ADR mới với status `Supersedes ADR-000X` và đánh dấu ADR cũ `Superseded by ADR-000Y`. Lịch sử quyết định mới là giá trị.
- **Status:** `Proposed` → `Accepted` → (`Deprecated` | `Superseded`).
- **5 mục chuẩn:** Tiêu đề · Trạng thái · Bối cảnh · Quyết định · Hệ quả (cả tích cực lẫn tiêu cực — đừng giấu trade-off).

Lưu `docs/adr/0000-template.md` + từng ADR đánh số.

**ADR-0001 (ví dụ thật cho dự án này):**

```markdown
# ADR-0001: Greedy heuristic cho Relocation Engine (CRP)

## Trạng thái
Accepted — 2026-06-20

## Bối cảnh
Container Relocation Problem là NP-hard; không có lời giải tối ưu nhanh cho mọi
trường hợp. Quy mô mục tiêu: ≤ ~100 lô chồng chéo trong một lane. Yêu cầu phản
hồi tương tác (< 500 ms, xem NFR §7 của 01-overview) và giải thích được cho
người vận hành.

## Quyết định
Dùng greedy: trong số các lô đang chặn đường, ưu tiên dời lô có dự đoán lấy ra
muộn nhất / kích thước nhỏ nhất, đặt vào vị trí tạm gần nhất không tạo blocking
mới. Đồ thị blocking xây cục bộ trong lane.

## Hệ quả
Tích cực:
- Độ phức tạp thấp, real-time được ở quy mô nhỏ–vừa.
- Diễn giải được cho người dùng (so với branch-and-bound khó giải thích).
- Đủ tốt cho phần lớn ca thực tế.

Tiêu cực / trade-off đã biết:
- Không đảm bảo số bước di chuyển tối thiểu tuyệt đối.
- Sẽ cần đánh giá lại (beam search / B&B) nếu mở rộng kho rất lớn (>10k vị trí).
```

**Giá trị khi phỏng vấn:** khi bị hỏi "vì sao chọn cách này", bạn có sẵn câu trả lời đã suy nghĩ kỹ — phân biệt "làm vì hiểu" với "làm vì copy".

---

## 4. CHANGELOG.md — chuẩn Keep a Changelog

```markdown
# Changelog
Tất cả thay đổi đáng kể của dự án được ghi tại đây.
Định dạng theo Keep a Changelog; version theo SemVer.

## [Unreleased]

## [0.2.0] - 2026-07-15
### Added
- Relocation Engine với greedy heuristic cho CRP
- Unit test cho blocking graph construction
### Changed
- Tối ưu spatial query: blocking graph từ O(n²) xuống O(n log n)

## [0.1.0] - 2026-06-20
### Added
- 3D viewer cơ bản (Three.js + React Three Fiber)
- CRUD tồn kho (zone/aisle/rack/bin)
```

> Ngày tháng ở trên là minh họa. Giữ section `[Unreleased]` để gom thay đổi trước khi gắn tag.

Duy trì file này (vài dòng mỗi lần) tạo cảm giác dự án phát triển có kế hoạch.

---

## 5. Dev Log — viết cho chính mình, viết thật

Khác README (cho người khác đọc), Dev Log track quá trình suy nghĩ, vấn đề gặp, cách giải:

```markdown
## 2026-06-25
- Vấn đề: blocking graph tính sai khi 2 lô cùng z_min (cùng tầng).
- Nguyên nhân: dùng >= thay vì > khi check "nằm trên".
- Đã sửa: phân biệt rõ "cùng tầng" vs "tầng trên".
- Học được: cần test case cho edge case "2 object cùng toạ độ biên" ngay từ đầu.
```

**Giá trị:** câu hỏi phỏng vấn "bạn gặp khó khăn gì, giải quyết sao?" cực phổ biến — Dev Log cho bạn câu trả lời cụ thể, có dẫn chứng thật, thay vì chung chung.

---

## 6. Algorithm Spec — giá trị kỹ thuật cao nhất

CRP/Relocation là phần "ngầu" nhất → một file riêng (`docs/algorithm-spec.md`), không gộp vào README. Khung:

1. Mô tả bài toán (input/output)
2. Vì sao khó (NP-hard, dẫn nguồn academic)
3. Thuật toán chọn (pseudocode rõ)
4. Độ phức tạp (Big-O)
5. Trade-off + hướng cải thiện
6. Test case minh họa (input cụ thể → output cụ thể)

Đây là file nhà tuyển dụng kỹ thuật giỏi sẽ đọc kỹ nhất.

---

## 7. Tích hợp vào nhịp làm việc, không phải việc làm thêm

| Tần suất | Việc | Thời gian |
|---|---|---|
| Mỗi khi merge 1 tính năng | Update CHANGELOG (3–5 dòng) | 5 phút |
| Mỗi quyết định kỹ thuật lớn | 1 ADR | 15–20 phút |
| Cuối buổi code (nếu có vấn đề) | Dev Log ngắn | 5–10 phút |
| Xong 1 module lớn | Algorithm Spec | 1–2 giờ, một lần |

**Nguyên tắc:** tài liệu hóa **nhẹ và liên tục**, không dồn cuối dự án — lúc đó bạn đã quên lý do thật của các quyết định.

---

*Dùng song song với [`02-git-workflow.md`](./02-git-workflow.md): mỗi PR quan trọng nên đi kèm cập nhật tài liệu tương ứng (CHANGELOG, ADR nếu cần).*
