# ADR-0004: Chiến lược client — Web → PWA → Capacitor (không native thuần)

## Trạng thái
Accepted — 2026-06-25

## Bối cảnh
Người dùng cuối là **nhân viên kho dùng điện thoại**, vừa lấy hàng (tay bận) vừa mang thiết bị di chuyển trong kho. Bối cảnh này đặt ra các nhu cầu: thao tác **một tay**, **quét mã bằng camera**, dùng được khi **mạng chập chờn**, và **có thể** sau này cần đưa lên **App Store (iOS) + Google Play (Android)**.

Câu hỏi kiến trúc: hiện thực client (giao diện người dùng) theo hướng nào để vừa phục vụ tốt bối cảnh trên, vừa **không phải làm lại từ đầu** khi nhu cầu lớn dần (từ web → app store)?

Ràng buộc đã biết:
- Frontend hiện tại là **web** (Next.js + React + React Three Fiber cho 3D) — đã có sẵn, không muốn vứt đi.
- Backend (Spring Boot, REST + WebSocket) **giữ nguyên** cho mọi loại client.
- Dự án định vị "kho vừa", đội phát triển nhỏ → tối ưu **công sức bỏ ra / lợi ích thu về**.
- 3D nặng, màn hình điện thoại nhỏ → cần cân nhắc bản hiển thị rút gọn cho điện thoại.

## Quyết định
Đi theo lộ trình tiến hóa **một codebase web**, mỗi bước xây trên bước trước, **không làm lại từ đầu**:

1. **Web responsive / mobile-first** — giao diện co giãn theo màn hình, thao tác một tay (nút lớn, hành động chính ở tầm ngón cái). Đây là nền cho mọi hướng sau.
2. **PWA** (Progressive Web App — web "cài" được lên màn hình chính, chạy full-screen, hỗ trợ offline qua Service Worker — bộ nhớ đệm phía trình duyệt). Cho trải nghiệm gần như app mà **không qua store**.
3. **Capacitor** (công cụ bọc web app vào "vỏ" native để nộp store) **khi** thật sự cần lên App Store / Google Play — dùng lại ~95% code web, thêm plugin native (camera, push, offline) khi cần.

**Không chọn** (ở thời điểm này):
- **Native thuần (Swift + Kotlin):** làm lại 2 lần từ đầu, nuôi 2 team — quá đắt, app kho không cần hiệu năng/phần cứng tới mức đó.
- **React Native / Flutter:** tận dụng backend nhưng **phải làm lại toàn bộ UI** (đặc biệt phần 3D phải viết lại cho native) — chỉ cân nhắc *sau này* nếu Capacitor không đủ mượt cho 3D, và khi đó quyết định dựa trên **dữ liệu thật**, không đoán trước.

Nguyên tắc kèm theo: **không vội lên store.** Validate sản phẩm bằng web/PWA trước; chỉ đóng gói lên store khi có người dùng thật và nhu cầu rõ ràng.

## Hệ quả

Tích cực:
- **Không phí công:** mỗi bước (web → PWA → Capacitor) tái dùng bước trước; không bao giờ "làm lại từ đầu".
- **Đúng cách ngành kho/logistics làm thực tế:** app kho phần lớn là nội bộ doanh nghiệp, chạy trên máy quét Android / điện thoại qua web/PWA; khi cần SaaS lên store thì bọc Capacitor — hiếm khi native thuần.
- **Một codebase, một đội:** dùng lại Next.js/React + toàn bộ API backend; không phải nuôi team iOS + Android riêng.
- **Offline + camera scan khả thi:** Service Worker (PWA) cho offline-nhẹ; camera qua web (getUserMedia) cho quét mã; Capacitor plugin nâng cấp khi cần.
- Ledger append-only của hệ thống **rất hợp offline:** thao tác offline = các movement chờ gửi; gửi lại an toàn nếu kèm **idempotency key** (xem [system-design-proposal.md](../system-design-proposal.md) §B2).

Tiêu cực / đánh đổi (không giấu):
- **3D trên điện thoại nặng** (ngốn GPU/pin, màn hình nhỏ) → phải làm **bản rút gọn cho điện thoại** (2D/danh sách hoặc 3D một lane), không render cả kho. Đây là việc thiết kế UI thêm.
- **WebView của Capacitor không mượt bằng native thật** cho đồ họa 3D nặng — nếu sau này 3D là điểm nghẽn trên app store, có thể phải tách riêng phần 3D sang native (React Native). Rủi ro này chấp nhận được vì màn hình chính trên điện thoại là *danh sách bước CRP/putaway*, không phải 3D toàn cảnh.
- **Lên store cần chi phí + hạ tầng** (đường dài): tài khoản Apple Developer (~99 USD/năm), Google Play (~25 USD một lần), **máy Mac hoặc build cloud để build iOS**, và Apple xét app "bọc web" kỹ hơn (cần tính năng native rõ ràng như camera scan để qua dễ).
- **HTTPS bắt buộc** (cho camera API + bảo mật) ở mọi hướng.

## Liên quan
- [system-design-proposal.md](../system-design-proposal.md) — mục offline/idempotency (B2), realtime (C).
- [business.md](../business.md) — vai trò operator, quy trình CRP/putaway (màn hình chính trên điện thoại).
- [ADR-0002](./0002-backend-spring-boot.md) — backend giữ nguyên cho mọi client.
