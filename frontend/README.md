# Frontend — Quản lý đề tài sinh viên

Frontend dùng React, TypeScript, Vite và React Router, kết nối API Spring Boot bằng session cookie và CSRF.

## Chạy local

```powershell
cd frontend
npm install
npm run dev
```

Vite chạy mặc định tại `http://localhost:5173`. Các lời gọi đến `/api` được chuyển tiếp đến Spring Boot ở `http://localhost:8080` khi chạy development.

Nếu cần đổi cổng backend vì `8080` đang bận, đặt `BACKEND_PORT` trong `.env` ở thư mục gốc repo; Vite dev proxy sẽ tự trỏ sang cổng mới đó.

## Cấu hình API

Tạo file `.env.local` nếu backend dùng địa chỉ khác:

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

Lớp Axios dùng chung nằm ở `src/api/client.ts`; hợp đồng đăng nhập khởi tạo nằm ở `src/api/auth.ts`.

## Kiểm tra

```powershell
npm test
npm run lint
npm run build
```

`build` kiểm tra TypeScript trước khi đóng gói. Các kiểm thử trong `tests/` dùng DOM mô phỏng và API giả lập, bao gồm menu, tìm kiếm nhiều trang, thử tải lại, mở cấu hình đánh giá đã lưu, chấm điểm và đồng bộ thông báo.

Giảng viên mở **Chấm điểm đề tài** (`/score-sheets`) để lưu nháp hoặc nộp phiếu chấm được phân công. Phiếu đã nộp hoặc khóa chỉ cho xem. Mục **Tiêu chuẩn đánh giá** cho phép chọn lại thành phần đã lưu để tiếp tục cấu hình sau khi tải lại trang.
