# Quản lý đề tài sinh viên

Nền tảng quản lý vòng đời đề tài sinh viên: đề xuất và duyệt đề tài, lập nhóm,
đăng ký, theo dõi tiến độ, nộp báo cáo, tổ chức hội đồng, chấm điểm và công bố
kết quả.

## Thành phần chính

- `backend/`: Spring Boot REST API, Java 21 và MySQL 8.4.
- `frontend/`: React, TypeScript và Vite.
- `database/`: bootstrap database mới, Flyway migration và seed demo tùy chọn.

## Chức năng

- Quản lý tài khoản, vai trò, khoa, bộ môn, học kỳ và đợt đăng ký.
- Quản lý đề tài, giảng viên hướng dẫn, nhóm sinh viên và phân công đề tài.
- Theo dõi mốc tiến độ, báo cáo, phản hồi và tệp đính kèm.
- Cấu hình rubric, phân công chấm, hội đồng, lịch bảo vệ, kết quả và yêu cầu
  đổi điểm.
- Thông báo trong hệ thống và email theo cấu hình quản trị.

## Chạy nhanh

Tạo `.env` từ `.env.example`, đặt ít nhất `MYSQL_ROOT_PASSWORD` và
`MYSQL_APP_PASSWORD`, rồi chạy:

```powershell
docker compose up --build
```

Ứng dụng mặc định tại `http://localhost:5173`. Với volume MySQL mới, Docker
tự khởi tạo schema và dữ liệu nền; database đã có dữ liệu được nâng cấp bằng
Flyway, không chạy lại bootstrap và không xóa volume.

Để dùng dữ liệu demo cục bộ trên volume mới:

```powershell
docker compose --env-file docker-compose.demo.env -p qldetai-demo -f docker-compose.yml -f docker-compose.demo.yml up --build -d
```

Seed demo không được nạp trong cấu hình mặc định và tự dừng nếu database đã có
dữ liệu ứng dụng.
