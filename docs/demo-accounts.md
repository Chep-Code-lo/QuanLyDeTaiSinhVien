# Tài khoản demo

Bộ dữ liệu `database/seed/demo-data.sql` chỉ dành cho local/demo. Docker mặc định không nạp demo; chủ động dùng `docker compose -f docker-compose.yml -f docker-compose.demo.yml up --build -d` trên volume MySQL mới để nạp. Không dùng cấu hình này trên VPS.
Tất cả tài khoản dùng mật khẩu `Demo@123`; không sử dụng ở môi trường thật.

| Vai trò | Tài khoản | Ghi chú |
| --- | --- | --- |
| Quản trị | `admin@demo.local` | Quản trị hệ thống |
| Trưởng khoa | `dean@demo.local` | Giảng viên, nhiệm vụ `DEAN` khoa CNTT |
| Trưởng bộ môn | `head-cnpm@demo.local` | Giảng viên, nhiệm vụ `DEPARTMENT_HEAD` bộ môn CNPM |
| Giảng viên | `lecturer01@demo.local` đến `lecturer04@demo.local` | Danh bạ, hướng dẫn, phản biện và hội đồng |
| Sinh viên | `student01@demo.local` đến `student10@demo.local` | Có nhóm/đăng ký/tiến độ/kết quả theo từng đợt |

## Dữ liệu nghiệp vụ mẫu

- Một khoa CNTT, ba bộ môn và ba đợt: Đồ án môn học đang mở, TLCN đã đóng và
  KLTN đã đóng.
- Mỗi đợt yêu cầu nhóm từ 3 đến 5 sinh viên. Dữ liệu có năm nhóm đúng giới hạn
  này.
- Đợt đang mở có nhóm đã nhận đề tài và mốc báo cáo, nhóm có lời mời đang chờ
  phản hồi, cùng một nhóm có hồ sơ đăng ký đề tài đang chờ duyệt.
- Đợt KLTN có hội đồng, phản biện, phiếu điểm đã khóa và một kết quả đã công bố
  cho ba sinh viên.

Chỉ khi chấp nhận xóa toàn bộ dữ liệu local, có thể nạp lại dữ liệu mẫu bằng `docker compose -f docker-compose.yml -f docker-compose.demo.yml down -v` rồi `docker compose -f docker-compose.yml -f docker-compose.demo.yml up --build -d`. Không thực hiện thao tác reset này trên VPS hoặc database cần giữ dữ liệu.
