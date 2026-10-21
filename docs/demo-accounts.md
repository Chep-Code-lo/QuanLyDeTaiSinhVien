# Tài khoản demo

Bộ dữ liệu này chỉ dành cho Docker cục bộ và được nạp khi volume MySQL mới.
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

Để nạp lại dữ liệu mẫu, chạy `docker compose down -v` rồi `docker compose up --build -d`.
