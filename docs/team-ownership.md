# Phân công mã nguồn

Tài liệu này xác định người phụ trách chính để tránh sửa chồng chéo. Khi thay
đổi giao diện hoặc API liên quan nhiều khu vực, người thực hiện phối hợp với
chủ sở hữu của khu vực còn lại.

| Thành viên | Folder/khu vực phụ trách chính | Phạm vi nghiệp vụ |
| --- | --- | --- |
| Nguyễn Thị Ngọc Quí | `frontend/src/app/`, `layouts/`, `components/`, `context/`, `routes/`, `assets/`, `config/`, `styles/`, `types/`, `utils/`; trang và API phía sinh viên | Khung ứng dụng, đăng nhập/trạng thái phiên, điều hướng, component dùng chung và luồng sinh viên: đề tài, nhóm, đăng ký, tiến độ, kết quả |
| Nguyễn Lê Hữu Chí | Các trang public/quản trị/giảng viên trong `frontend/src/pages/`; `frontend/src/features/notifications/`; API frontend tương ứng, gồm dashboard và thông báo | Giao diện trang chủ, đăng nhập, dashboard, quản lý học vụ, tiêu chuẩn đánh giá, phân công, chấm điểm, thông báo và quản trị giao diện |
| Nguyễn Vũ Luân | `backend/src/main/java/com/example/qldetai/common/audit/`, `common/observability/`, `common/persistence/`, `config/`, `exception/`, `security/`, `system/`, `feature/identity/`, `feature/academic/`, `feature/dashboard/`; `backend/src/main/resources/`, `database/`, `docker-compose.yml`, `.env.example`, `scripts/` | Nền tảng, xác thực/phân quyền, audit/logging, persistence dùng chung, tài khoản, khoa/bộ môn/học kỳ, đợt đăng ký, dashboard tổng hợp, schema/migration và triển khai |
| Phạm Hồng Anh | `backend/src/main/java/com/example/qldetai/feature/topic/`, `feature/groupregistration/`, `feature/project/`, `feature/communication/`; API/trang frontend về đề tài, nhóm và đăng ký | Đề tài, GVHD, nhóm, lời mời, đăng ký đề tài, giao đề tài, thông báo trong hệ thống và email |
| Phạm Hữu Ngọc | `backend/src/main/java/com/example/qldetai/feature/progress/`, `feature/evaluation/`; API/trang frontend về tiến độ, báo cáo, hội đồng, chấm điểm và kết quả | Mốc báo cáo, nộp tệp, phản hồi, hội đồng/bảo vệ, bộ tiêu chí, phiếu điểm, công bố và tra cứu kết quả |

## Nghiệp vụ và vị trí mã

| Nghiệp vụ | Backend | Frontend/route |
| --- | --- | --- |
| Tài khoản, đăng nhập, phân quyền | `feature/identity/`, `config/SecurityConfig.java` | `/login`, `/account`, `/admin-users` |
| Dashboard tổng hợp | `feature/dashboard/` | `/dashboard` |
| Khoa, bộ môn, học kỳ, đợt đăng ký | `feature/academic/` | `/academic-management`, `/admin-appointments` |
| Đề tài, hướng dẫn, nhóm và đăng ký | `feature/topic/`, `feature/groupregistration/`, `feature/project/` | `/topics`, `/groups`, `/registrations` |
| Tiến độ và báo cáo | `feature/progress/` | `/progress` |
| Bộ tiêu chí, phản biện, hội đồng, chấm điểm | `feature/evaluation/` | `/evaluations`, `/evaluator-assignment`, `/score-sheets` |
| Công bố/tra cứu kết quả | `feature/evaluation/` | `/results` |
| Thông báo, email, nhật ký | `feature/communication/` | `/announcements`, `features/notifications/`, `components/Header.tsx` |

## Quy ước bàn giao

- API thay đổi phải cập nhật DTO, test backend và module gọi API ở frontend.
- Migration/schema, Docker và dữ liệu demo do Nguyễn Vũ Luân rà soát trước khi merge.
- Dashboard là dữ liệu tổng hợp xuyên feature: Nguyễn Vũ Luân phụ trách API, Nguyễn Lê Hữu Chí phụ trách màn hình.
- Thông báo cần phối hợp giữa Phạm Hồng Anh (backend) và Nguyễn Lê Hữu Chí (frontend).
- Mỗi thay đổi xuyên feature cần ghi rõ người phối hợp trong pull request hoặc nhật ký nhóm.
