Hoàn tất tính năng trên nhánh hiện tại, theo quy ước trong [docs/02](../../docs/02-git-workflow.md) và [docs/03](../../docs/03-documentation.md):

1. Chạy build + test; nếu fail thì dừng và báo tôi.
2. Xem `git status`; đề xuất các commit nhỏ theo Conventional Commits cho phần chưa commit.
3. Cập nhật `CHANGELOG.md` mục `[Unreleased]`.
4. Nếu nhánh này có quyết định kỹ thuật lớn, soạn nháp ADR trong `docs/adr/` (đánh số tiếp theo, format Nygard).
5. Tóm tắt nội dung PR (làm gì, vì sao) để tôi tạo PR.

KHÔNG tự merge/push; dừng cho tôi xác nhận ở mỗi bước có side effect.
