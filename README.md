# Quản lý đề tài sinh viên

Nền tảng quản lý đề tài, nhóm sinh viên, tiến độ, chấm điểm và thông báo.

## Cấu trúc

- `backend/`: Spring Boot REST API.
- `frontend/`: React + Vite + TypeScript.
- `database/`: schema MySQL nguồn.
- `docs/`: tài liệu kiến trúc, API và quy ước phát triển.

## Chạy cục bộ

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

Khi chạy local không dùng Docker, có thể bật dữ liệu demo gồm admin, giảng viên, sinh viên, nhiệm vụ trưởng khoa và một đợt KLTN đang mở:

```powershell
$env:DEMO_DATA_ENABLED = "true"
$env:DEMO_PASSWORD = "Demo@123"
```

Tài khoản demo:

| Vai trò | Tài khoản | Mật khẩu |
| --- | --- | --- |
| Quản trị | `admin@demo.local` | `Demo@123` |
| Giảng viên / trưởng khoa | `lecturer@demo.local` | `Demo@123` |
| Sinh viên | `student@demo.local` | `Demo@123` |

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
   DEMO_DATA_ENABLED=true
   DEMO_PASSWORD=Demo@123
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

Ghi chú:

- MySQL được expose ra host qua cổng `3307` mặc định để tránh đụng MySQL cục bộ.
- Lần khởi động đầu tiên, container MySQL sẽ tự import `database/ql_de_tai_sinh_vien_schema.sql` và tạo user ứng dụng.
- Dữ liệu database được lưu trong volume `mysql_data`.
- File upload của backend được lưu trong volume `backend_files`.
- Frontend container reverse proxy `/api/*` sang backend nên không cần chỉnh `VITE_API_BASE_URL` khi chạy compose.
- Nếu muốn import lại schema từ đầu, chạy `docker compose down -v` rồi `docker compose up --build`.

### Nâng cấp database đang có dữ liệu

Thay đổi file schema không tự cập nhật volume MySQL đã khởi tạo trước đó.
Nếu backend báo thiếu cột cấu hình của `registration_periods`, chạy migration
sau từ thư mục gốc dự án; migration chỉ thêm phần còn thiếu và giữ nguyên dữ liệu,
lịch đăng ký cũng như các cấu hình đã có:

```powershell
Get-Content database/migrations/20261018_registration_period_rules.sql -Raw |
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot ql_de_tai_sinh_vien'
```

Có thể chạy lại migration an toàn. Việc nâng cấp này không gia hạn các đợt đã
hết hạn; Trưởng khoa hoặc quản trị viên mở đợt mới tại **Quản lý Học thuật**.

### Reset dữ liệu demo

Khi không cần giữ dữ liệu local, reset toàn bộ dữ liệu Docker rồi khởi tạo lại
schema và tài khoản demo bằng hai lệnh:

```powershell
docker compose down -v
docker compose up --build -d
```

Lệnh reset xóa cả dữ liệu MySQL lẫn file upload trong volume Docker. Chỉ dùng
cho môi trường phát triển/demo.
