# Từ điển thuật ngữ — Stockpile-3D

> Giải thích ngắn gọn các thuật ngữ kỹ thuật, thư viện và khái niệm dùng trong dự án — cho người đang học. Sắp theo nhóm.

## Kiến trúc & dữ liệu

- **Decision-support system** (hệ thống hỗ trợ ra quyết định) — phần mềm *tính toán đề xuất* cho con người, khác phần mềm chỉ lưu/hiển thị dữ liệu.
- **Movement ledger** (sổ cái di chuyển) — bảng chỉ ghi-thêm, lưu *mọi* thay đổi vật lý của kho theo thứ tự thời gian; là "nguồn sự thật".
- **Append-only** (chỉ ghi thêm) — dữ liệu chỉ được thêm mới, không bao giờ sửa/xóa dòng cũ → giữ được lịch sử đầy đủ để truy vết.
- **Projection** (hình chiếu / bảng dẫn xuất) — bảng trạng thái hiện tại (`placement`) được *tính lại* từ ledger, không phải dữ liệu gốc độc lập.
- **Replay** (phát lại) — đọc lại toàn bộ ledger từ đầu theo thứ tự để dựng lại trạng thái — cách kiểm chứng projection đúng.
- **Source of truth** (nguồn sự thật) — nơi dữ liệu được coi là đúng nhất khi có mâu thuẫn (ở đây: ledger thắng).
- **Blocking graph** (đồ thị chặn) — đồ thị có hướng mô tả "lô nào chặn lô nào" khi lấy hàng; trái tim của thuật toán CRP.
- **DAG** (đồ thị có hướng không chu trình) — đồ thị mà không thể đi vòng về điểm cũ; đồ thị blocking là DAG khi xếp hàng hợp lệ.
- **Lane** (làn/dãy) — một cụm vị trí `(zone, aisle, rack)`; quan hệ blocking chỉ xét *trong* một lane cho đơn giản.
- **NFR — Non-Functional Requirements** (yêu cầu phi chức năng) — các mục tiêu định lượng như tốc độ, quy mô (vd CRP < 500ms), khác với "tính năng làm được gì".

## Thuật toán (giá trị lõi)

- **CRP — Container/Block Relocation Problem** (bài toán di dời khối) — cho một lô cần lấy, tìm chuỗi di chuyển *tối thiểu* để giải phóng nó.
- **SLAP — Storage Location Assignment Problem** (bài toán gán vị trí lưu trữ) — chọn vị trí trống tốt nhất để cất lô mới.
- **NP-hard** (lớp bài toán "khó") — bài toán mà chưa ai biết cách giải tối ưu nhanh cho mọi trường hợp khi dữ liệu lớn.
- **Heuristic** (thuật toán kinh nghiệm/xấp xỉ) — cách giải "đủ tốt và nhanh", không đảm bảo tối ưu tuyệt đối.
- **Greedy** (tham lam) — chiến lược chọn bước tốt nhất *tại mỗi thời điểm* mà không tính toàn cục; đơn giản, nhanh, dễ giải thích.
- **Big-O** (ký hiệu độ phức tạp) — cách mô tả thời gian/bộ nhớ thuật toán tăng thế nào theo kích thước dữ liệu (vd `O(n²)`).

## Backend (Java + Spring Boot)

- **Spring Boot** (khung phát triển Java) — bộ công cụ dựng ứng dụng Java nhanh, cấu hình sẵn nhiều thứ.
- **Spring Web / REST controller** — lớp nhận request HTTP và trả JSON (REST = kiểu thiết kế API qua HTTP).
- **Spring Data JPA** — thư viện giúp thao tác database bằng đối tượng Java thay vì viết SQL tay.
- **JPA / Hibernate / ORM** (ánh xạ đối tượng-quan hệ) — kỹ thuật biến bảng DB thành class Java và ngược lại; Hibernate là bản hiện thực phổ biến.
- **Entity** (thực thể) — một class Java ánh xạ tới một bảng DB (vd `Lot` ↔ bảng `lot`).
- **Repository** (kho truy cập dữ liệu) — interface cung cấp sẵn các thao tác CRUD với DB; Spring tự sinh code.
- **CRUD** — Create/Read/Update/Delete, bốn thao tác cơ bản với dữ liệu.
- **DTO — Data Transfer Object** (đối tượng truyền dữ liệu) — class riêng để nhận/trả qua API, tách khỏi entity để không lộ chi tiết DB.
- **Bean Validation** (kiểm tra dữ liệu đầu vào) — annotation như `@NotNull`, `@Positive` để tự kiểm request hợp lệ.
- **Flyway** (công cụ di trú schema) — quản lý thay đổi cấu trúc DB bằng các file SQL đánh số phiên bản (`V1`, `V2`…).
- **Migration** (di trú schema) — một bước thay đổi cấu trúc DB (tạo bảng, thêm cột…), versioned để chạy đúng thứ tự.
- **Native query** (truy vấn SQL thuần) — viết SQL tay thay vì để JPA sinh, dùng khi cần kiểm soát/hiệu năng.
- **Transaction** (giao dịch) — nhóm thao tác DB "được ăn cả ngã về không": hoặc thành công hết, hoặc hủy hết (`@Transactional`).
- **Bean / Dependency Injection** (tiêm phụ thuộc) — Spring tự tạo và "tiêm" đối tượng cần dùng vào nơi khai báo, đỡ phải `new` thủ công.
- **Lombok** (thư viện giảm code lặp) — tự sinh getter/setter/constructor qua annotation (`@Getter`, `@RequiredArgsConstructor`).
- **Actuator** — module Spring cung cấp endpoint giám sát sẵn (vd `/actuator/health`).
- **springdoc-openapi / Swagger UI** — tự sinh tài liệu API (OpenAPI) và trang thử API trực quan.
- **OpenAPI** (chuẩn mô tả API) — định dạng máy-đọc-được mô tả các endpoint; dùng để sinh client tự động.
- **CORS — Cross-Origin Resource Sharing** (chia sẻ tài nguyên khác nguồn) — cơ chế cho phép trình duyệt ở domain này gọi API ở domain khác.
- **Maven** (công cụ build Java) — quản lý thư viện phụ thuộc và biên dịch/đóng gói dự án (`pom.xml`).
- **BOM — Bill of Materials** (danh mục phiên bản) — file gom sẵn version tương thích của nhiều thư viện để khỏi tự chọn.

## Frontend (Next.js + 3D)

- **Next.js** (khung React) — framework dựng web bằng React, có sẵn routing và render phía server.
- **React** (thư viện giao diện) — thư viện JS dựng UI theo component (khối giao diện tái dùng).
- **TypeScript** (JS có kiểu) — JavaScript thêm kiểm tra kiểu dữ liệu lúc viết code, bắt lỗi sớm.
- **Server Component / SSR** (render phía máy chủ) — trang được dựng sẵn ở server rồi gửi HTML về, nhanh và sẵn dữ liệu.
- **Client Component** — component chạy trong trình duyệt (cần cho thao tác tương tác/WebGL); đánh dấu `"use client"`.
- **Three.js** (thư viện 3D) — thư viện vẽ đồ họa 3D trong trình duyệt bằng WebGL.
- **WebGL** (chuẩn vẽ 3D trên web) — API cho phép dùng GPU để vẽ đồ họa trong trình duyệt.
- **React Three Fiber (R3F)** — cách viết Three.js bằng cú pháp component React, gọn và dễ quản lý hơn.
- **drei** — bộ tiện ích sẵn cho R3F (camera điều khiển, lưới, helper…).
- **InstancedMesh** (lưới thực thể hàng loạt) — kỹ thuật vẽ rất nhiều vật thể *giống nhau* bằng một lệnh GPU để đạt tốc độ cao (mục tiêu ~60fps với ~50k vị trí).
- **Frustum culling** (cắt bỏ ngoài tầm nhìn) — không vẽ vật thể nằm ngoài khung nhìn camera để tiết kiệm hiệu năng.
- **OrbitControls** (điều khiển quỹ đạo) — cho phép xoay/zoom/di chuyển camera quanh cảnh bằng chuột.
- **Y-up / Z-up** (quy ước trục đứng) — Three.js coi trục Y là "lên trời", còn data kho coi Z là chiều cao → phải đổi trục khi vẽ.

## Mobile / triển khai client (xem [ADR-0004](./adr/0004-client-strategy-web-pwa-capacitor.md))

- **Web app** (ứng dụng web) — chạy trong trình duyệt, không tải/cài; vào bằng địa chỉ web. Stockpile-3D hiện là web app.
- **Native app** (ứng dụng gốc) — app tải từ App Store/Google Play, cài vào máy; viết bằng Swift (iOS) / Kotlin (Android).
- **Responsive / mobile-first** (giao diện co giãn / ưu tiên điện thoại) — UI tự thay đổi theo kích thước màn hình; "mobile-first" = thiết kế cho điện thoại trước rồi mới mở rộng lên màn hình lớn.
- **PWA — Progressive Web App** (web tiến bộ) — web nhưng "cài" được lên màn hình chính, chạy full-screen, hỗ trợ offline; không qua app store.
- **Service Worker** (tiến trình nền của trình duyệt) — đoạn mã chạy nền giúp PWA cache dữ liệu để **dùng được khi mất mạng** + đồng bộ lại khi có mạng.
- **getUserMedia** (API camera của web) — cho phép web truy cập camera điện thoại (để quét barcode); yêu cầu HTTPS.
- **Capacitor** (công cụ bọc web thành app) — đóng gói web app vào "vỏ" native để nộp App Store/Play, dùng lại ~95% code web.
- **WebView** (trình duyệt nhúng) — thành phần hiển thị web bên trong một app native; Capacitor chạy web của bạn trong WebView.
- **React Native / Flutter** (framework app đa nền tảng) — viết một lần ra cả iOS + Android; React Native dùng React/JS, Flutter dùng Dart.
- **MDM — Mobile Device Management** (quản lý thiết bị di động) — hệ thống doanh nghiệp cài/quản app cho thiết bị nhân viên mà không qua app store công khai.

## Quy trình & công cụ

- **ADR — Architecture Decision Record** (bản ghi quyết định kiến trúc) — tài liệu ngắn ghi *quyết định lớn + lý do + đánh đổi*, không sửa sau khi chốt.
- **Conventional Commits** (chuẩn đặt tên commit) — quy ước viết commit dạng `feat:`, `fix:`, `docs:`… để lịch sử đọc như changelog.
- **CHANGELOG / Keep a Changelog** (nhật ký thay đổi) — file liệt kê thay đổi theo phiên bản, chuẩn "Keep a Changelog".
- **SemVer — Semantic Versioning** (đánh số phiên bản theo ngữ nghĩa) — `MAJOR.MINOR.PATCH` (vd `v0.1.0`), tăng số theo mức độ thay đổi.
- **Fast-forward merge** (gộp tiến thẳng) — khi nhánh đích chưa có commit mới, gộp chỉ là "dời con trỏ" tới, không tạo commit gộp.
- **Testcontainers** (thư viện test với container) — chạy một PostgreSQL thật trong Docker khi test, sạch và giống production.
- **Docker / container** (đóng gói máy ảo nhẹ) — công nghệ chạy phần mềm trong môi trường cô lập, nhẹ hơn máy ảo.
- **WSL2 — Windows Subsystem for Linux** (Linux nhúng trong Windows) — lớp chạy Linux thật bên trong Windows; Docker Desktop cần nó.
- **MockMvc** (công cụ test web) — giả lập gọi HTTP tới controller trong test mà không cần chạy server thật.
- **noreply email** (email ẩn của GitHub) — email dạng `…@users.noreply.github.com` để commit được tính cho bạn mà không lộ email thật.
