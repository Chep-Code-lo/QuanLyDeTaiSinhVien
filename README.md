# Quản lý đề tài sinh viên

Nền tảng quản lý đề tài, nhóm sinh viên, tiến độ, chấm điểm và thông báo.

## Cấu trúc

- `backend/`: Spring Boot REST API.
- `frontend/`: React + Vite + TypeScript.
- `database/`: schema MySQL nguồn.
- `docs/`: tài liệu kiến trúc, API và quy ước phát triển.

## Chạy cục bộ

1. Import `database/ql_de_tai_sinh_vien_schema.sql` vào MySQL 8.4+.
2. Đặt biến môi trường backend (không ghi mật khẩu thật vào Git):

   ```powershell
   $env:DB_USERNAME = "ql_de_tai_app"
   $env:DB_PASSWORD = "your-local-password"
   ```

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

Backend nhận cấu hình database qua `DB_URL`, `DB_USERNAME`, và `DB_PASSWORD`; không đưa mật khẩu thật vào Git.

Để tạo dữ liệu demo gồm admin, giảng viên, sinh viên, nhiệm vụ trưởng khoa và một đợt KLTN hợp lệ:

```powershell
$env:DEMO_DATA_ENABLED = "true"
$env:DEMO_PASSWORD = "a-demo-password-with-at-least-8-characters"
```

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
   DEMO_DATA_ENABLED=false
   DEMO_PASSWORD=
   ```

2. Khởi động toàn bộ stack:

   ```powershell
   docker compose up --build
   ```

3. Mở ứng dụng tại `http://localhost:5173`.

4. API backend vẫn có thể truy cập trực tiếp tại `http://localhost:8080/api/v1`.

Ghi chú:

- MySQL được expose ra host qua cổng `3307` mặc định để tránh đụng MySQL cục bộ.
- Lần khởi động đầu tiên, container MySQL sẽ tự import `database/ql_de_tai_sinh_vien_schema.sql` và tạo user ứng dụng.
- Dữ liệu database được lưu trong volume `mysql_data`.
- File upload của backend được lưu trong volume `backend_files`.
- Frontend container reverse proxy `/api/*` sang backend nên không cần chỉnh `VITE_API_BASE_URL` khi chạy compose.
- Nếu muốn import lại schema từ đầu, chạy `docker compose down -v` rồi `docker compose up --build`.
