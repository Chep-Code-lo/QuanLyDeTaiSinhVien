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
