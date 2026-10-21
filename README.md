# Quản lý đề tài sinh viên

Nền tảng quản lý đề tài, nhóm sinh viên, tiến độ, chấm điểm và thông báo.

## Cấu trúc

- `backend/`: Spring Boot REST API.
- `frontend/`: React + Vite + TypeScript.
- `database/ql_de_tai_sinh_vien_schema.sql`: file SQL chính, gồm cấu trúc database và vai trò hệ thống.
- `database/seed/demo-data.sql`: dữ liệu mẫu riêng cho local/demo, chỉ nạp khi chủ động chọn.
- `docs/`: tài liệu kiến trúc, API và quy ước phát triển.

## Chạy cục bộ

**File database chính duy nhất:** [`database/ql_de_tai_sinh_vien_schema.sql`](database/ql_de_tai_sinh_vien_schema.sql). Khi tạo database mới trên MySQL 8.4, mở file này trong MySQL Workbench và chạy toàn bộ, hoặc dùng MySQL CLI: `SOURCE E:/QuanLyDeTaiSinhVien/database/ql_de_tai_sinh_vien_schema.sql;` (đổi đường dẫn theo máy). File tự chọn database `ql_de_tai_sinh_vien`, chỉ tạo cấu trúc và ba vai trò hệ thống; không tạo tài khoản hoặc dữ liệu nghiệp vụ mẫu. Với Docker, việc import diễn ra tự động khi volume MySQL còn trống.

Database đang có dữ liệu tiếp tục dùng nguyên volume hiện tại và chỉ áp dụng migration cần thiết trong `database/migrations/`. Không import lại file chính hoặc reset volume để nâng cấp. Các file trong `database/checks/` chỉ phục vụ kiểm tra dữ liệu.

1. Khởi động MySQL local.

   Cách nhanh nhất là dùng MySQL đã có trong `docker compose`, service này mặc định expose ra `localhost:3307`.

2. Tạo `backend/.env` hoặc `.env` ở thư mục gốc repo từ `backend/.env.example`, rồi chỉnh lại nếu bạn không dùng cấu hình MySQL mặc định:

   ```powershell
   DB_URL=jdbc:mysql://localhost:3307/ql_de_tai_sinh_vien?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC
   DB_USERNAME=ql_de_tai_app
   DB_PASSWORD=change-me-local
   BACKEND_PORT=8080
   ```

   Nếu máy bạn đang có tiến trình khác chiếm `8080`, chỉ cần đổi `BACKEND_PORT`, ví dụ `8081`.

3. Chạy backend:

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

4. Chạy frontend:

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

Backend nhận cấu hình database qua `DB_URL`, `DB_USERNAME`, và `DB_PASSWORD`; có thể đặt trực tiếp trong shell, trong `backend/.env`, hoặc trong `.env` ở thư mục gốc repo. Frontend dev proxy cũng tự đọc `BACKEND_PORT` từ `.env` gốc nếu bạn đổi cổng backend.

Nếu deploy frontend và backend khác origin hoặc khác domain, cần cấu hình thêm:

```env
CORS_ALLOWED_ORIGINS=https://ten-mien-frontend-cua-ban
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=none
CSRF_COOKIE_SECURE=true
CSRF_COOKIE_SAME_SITE=none
```

Nếu dùng chung một domain và reverse proxy `/api/*` về backend thì không cần chuyển `same-site` sang `none`.

Docker mặc định không nạp dữ liệu demo, phù hợp để khởi tạo database sạch khi triển khai VPS. Nếu cần dữ liệu mẫu trên local với volume MySQL mới, chủ động dùng file cấu hình demo:

```powershell
docker compose -f docker-compose.yml -f docker-compose.demo.yml up --build -d
```

File `database/seed/demo-data.sql` chứa khoa CNTT, ba bộ môn, ba đợt đăng ký và dữ liệu từ đề tài đến đánh giá/kết quả. Có thể import riêng file này một lần sau file schema trên database demo còn trống. Danh sách tài khoản nằm tại [`docs/demo-accounts.md`](docs/demo-accounts.md). Không dùng override hoặc seed demo trên VPS. Khi khởi động lại môi trường demo, tiếp tục dùng đủ hai tham số `-f`; thêm override vào volume đã có dữ liệu không tự nạp seed.

Tài liệu hợp đồng các phần backend:

- `docs/backend-1-api.md`
- `docs/backend-2-api.md`
- `docs/backend-3-api.md`

## Chạy bằng Docker

Repo đã có sẵn cấu hình `docker-compose.yml` để chạy đồng thời MySQL, Spring Boot API và frontend React đã build production.

1. Tùy chọn: tạo file `.env` ở thư mục gốc để đổi cổng hoặc mật khẩu mặc định:

   ```env
   MYSQL_ROOT_PASSWORD=your-local-root-password
   MYSQL_APP_USER=ql_de_tai_app
   MYSQL_APP_PASSWORD=your-local-app-password
   FRONTEND_PORT=5173
   BACKEND_PORT=8080
   MYSQL_PORT=3307
   ```

2. Khởi động toàn bộ stack:

   ```powershell
   docker compose up --build
   ```

3. Mở ứng dụng tại `http://localhost:5173`.

4. API backend vẫn có thể truy cập trực tiếp tại `http://localhost:8080/api/v1`.

### Truy cập từ máy khác cùng Wi-Fi

Frontend Docker mặc định lắng nghe trên tất cả địa chỉ mạng của máy chủ, còn
MySQL và API vẫn chỉ mở nội bộ. Xem địa chỉ IPv4 của Wi-Fi bằng
`ipconfig`, rồi mở `http://<dia-chi-ip-wifi>:5173` từ máy còn lại. Ví dụ,
với IP `172.16.31.83`, dùng `http://172.16.31.83:5173`.

Trên Windows, cho phép kết nối TCP vào cổng `5173` trên mạng **Private** nếu
Windows Firewall chưa hỏi khi khởi động Docker. Không dùng Wi-Fi công cộng;
nếu mạng đang là **Public**, hãy chuyển nó sang **Private** trước.

### Chạy qua Cloudflare Tunnel trên Windows

Cài `cloudflared` nếu máy chưa có, rồi khởi động stack local:

```powershell
winget install --id Cloudflare.cloudflared -e
docker compose up -d --build
```

Từ thư mục gốc dự án, chạy:

```powershell
.\scripts\Start-CloudflareTunnel.ps1 -Build
```

Script tạo tunnel đến frontend `http://127.0.0.1:5173` (hoặc `FRONTEND_PORT` trong `.env`), in URL HTTPS, tự cập nhật `APP_BASE_URL`, CORS và cookie trong `.env`, rồi cập nhật container. MySQL phải đang chạy trước đó. Các lần chạy tiếp theo có thể bỏ `-Build` nếu mã nguồn không thay đổi.

Mở URL HTTPS được in ra để dùng cả giao diện lẫn `/api/v1`; không tạo tunnel riêng đến cổng backend. Nginx chuyển tiếp thông tin HTTPS đến Spring Boot, cookie dùng `Secure`, `SameSite=Lax` và không gắn cứng vào domain cũ.

[Quick Tunnel của Cloudflare](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/) có URL tạm thời. Khi tạo tunnel mới, script thay origin cũ trong cấu hình bằng URL mới. Log và trạng thái tiến trình lưu trong `.runtime/cloudflare/` và được Git bỏ qua. Script không dừng những tunnel khác đang chạy trên máy.

Script dùng cấu hình riêng `.runtime/cloudflare/quick.yml`, tránh tự nạp ingress của tunnel có tên trong `~/.cloudflared/config.yml`. Cấu hình cũ có thể khiến Quick Tunnel trả 404 dù đã kết nối thành công.

Để dừng tunnel do script quản lý:

```powershell
.\scripts\Start-CloudflareTunnel.ps1 -Stop
```

Nếu dùng tunnel có tên và domain riêng, trỏ dịch vụ Cloudflare về frontend `http://localhost:5173`, đặt `APP_BASE_URL` và `CORS_ALLOWED_ORIGINS` theo domain đó, rồi chạy `docker compose up -d --no-deps backend`. Không cần chạy script Quick Tunnel trong trường hợp này.

Ghi chú:

- MySQL được expose ra host qua cổng `3307` mặc định để tránh đụng MySQL cục bộ.
- Lần khởi động đầu tiên, container MySQL mặc định chỉ import `database/ql_de_tai_sinh_vien_schema.sql`: đủ 42 bảng, khóa/ràng buộc, view, trigger và ba vai trò hệ thống. Không nạp tài khoản hay dữ liệu demo. Chỉ cấu hình `docker-compose.demo.yml` mới bổ sung seed local. Docker tạo user kết nối MySQL theo cấu hình môi trường; đây không phải tài khoản đăng nhập ứng dụng.
- Mỗi lần `docker compose up`, service `schema-migrate` sẽ áp dụng migration email theo kiểu idempotent cho volume `mysql_data` hiện có, không xóa dữ liệu.
- Dữ liệu database được lưu trong volume `mysql_data`.
- File upload của backend được lưu trong volume `backend_files`.
- Frontend container reverse proxy `/api/*` sang backend nên không cần chỉnh `VITE_API_BASE_URL` khi chạy compose.
- Nếu muốn import lại schema từ đầu, chạy `docker compose down -v` rồi `docker compose up --build`.

### Gửi thông báo qua email

SMTP chỉ được cấu hình trong `.env`; không đưa `MAIL_USERNAME` hoặc `MAIL_PASSWORD` vào mã nguồn hay frontend. Sau khi điền các biến `MAIL_*`, đặt `EMAIL_ENABLED=true` rồi dựng lại container:

```powershell
docker compose up --build -d
```

Quản trị viên và giảng viên đều có mục **Cấu hình email**. Chọn mẫu mặc định để chỉnh nội dung thông báo tự động, hoặc chọn **Tạo mẫu mới** để đặt tên và lưu mẫu thư riêng dùng lại về sau. Mẫu riêng thuộc tài khoản tạo mẫu; có thể mở lại và chỉnh sửa trong **Mẫu của tôi**. Mẫu thư dùng văn bản thường, không cần viết HTML.

Để gửi thủ công, lưu nội dung rồi chọn nhóm hoặc từng người và nhấn **Gửi email**. Quản trị viên chọn được Sinh viên, Giảng viên hoặc người cụ thể toàn hệ thống; giảng viên chỉ chọn được Sinh viên hoặc từng sinh viên đang hướng dẫn, không gửi cho giảng viên khác. Backend kiểm tra phạm vi cho cả tìm kiếm, nhóm và ID cụ thể. Thành viên đã rời nhóm hoặc đăng ký chưa được duyệt không nằm trong phạm vi hướng dẫn. Không tự thêm tài khoản đăng nhập vào người nhận. Trạng thái giao hàng hiển thị trong **Nhật ký gửi email**; **Xem trước** chỉ hiển thị dữ liệu minh họa.

Để gửi kết quả điểm tự động, quản trị viên mở **Cấu hình email → Công bố kết quả điểm**, bật công tắc và lưu mẫu. Khi người có quyền công bố kết quả, hệ thống xếp hàng email chứa điểm tổng kết, phiên bản và đường dẫn `/results`, gửi riêng cho các sinh viên có tên trong bản kết quả đã chốt. Chấm/lưu phiếu hoặc chốt điểm chưa công bố không gửi email. Mẫu này không có nút gửi thủ công hoặc chọn người nhận khác. SMTP vẫn cần `EMAIL_ENABLED=true` và các biến `MAIL_*` hợp lệ để giao thư thực tế.

Database đang có dữ liệu cần áp dụng migration còn thiếu trong `database/migrations/`: `003-email-templates.sql` tạo bảng mẫu riêng; `004-result-published-email.sql` mở rộng CHECK event email để nhận `RESULT_PUBLISHED`. Migration 004 thay từng CHECK bằng một lệnh ALTER nguyên tử, giữ các event cũ và có thể chạy lại, không sửa/xóa các dòng dữ liệu. Docker Compose áp dụng qua `schema-migrate` khi dựng lại hệ thống. Nếu chạy ngoài Docker, thực thi migration trên database hiện có; không chạy lại toàn bộ schema bootstrap. File SQL chính đã chứa cấu trúc mới cho database khởi tạo từ đầu.

### Reset dữ liệu demo

Khi không cần giữ dữ liệu local, reset toàn bộ dữ liệu Docker rồi khởi tạo lại
schema và tài khoản demo bằng hai lệnh (chỉ local/demo):

```powershell
docker compose -f docker-compose.yml -f docker-compose.demo.yml down -v
docker compose -f docker-compose.yml -f docker-compose.demo.yml up --build -d
```

Lệnh reset xóa cả dữ liệu MySQL lẫn file upload trong volume Docker. Chỉ dùng
cho môi trường phát triển/demo.

Xem cấu trúc thư mục tại [`docs/project-structure.md`](docs/project-structure.md)
và phân công thư mục tại [`docs/team-ownership.md`](docs/team-ownership.md).
