# Frontend — Quản lý đề tài sinh viên

Giao diện khởi tạo dùng React, TypeScript, Vite và React Router. Các trang hiện là khung UI; chưa chứa thuật toán nghiệp vụ hoặc dữ liệu giả.

## Chạy local

```powershell
cd frontend
npm install
npm run dev
```

Vite chạy mặc định tại `http://localhost:5173`. Các lời gọi đến `/api` được chuyển tiếp đến Spring Boot ở `http://localhost:8080` khi chạy development.

## Cấu hình API

Tạo file `.env.local` nếu backend dùng địa chỉ khác:

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

Lớp Axios dùng chung nằm ở `src/api/client.ts`; hợp đồng đăng nhập khởi tạo nằm ở `src/api/auth.ts`.

## Kiểm tra build

```powershell
npm run build
```
