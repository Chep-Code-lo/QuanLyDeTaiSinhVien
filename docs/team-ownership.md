# Phân công mã nguồn

Tài liệu này xác định người phụ trách chính để tránh sửa chồng chéo. Khi thay
đổi giao diện hoặc API liên quan nhiều khu vực, người thực hiện phối hợp với
chủ sở hữu của khu vực còn lại.

| Thành viên | Folder/khu vực phụ trách chính | Phạm vi nghiệp vụ |
| --- | --- | --- |
| Nguyễn Thị Ngọc Quí | `frontend/src/app/`, `frontend/src/layouts/`, `frontend/src/components/`, `frontend/src/context/`, `frontend/src/routes/`; các trang và API phía sinh viên | Khung ứng dụng, đăng nhập/trạng thái phiên, điều hướng, component dùng chung, trải nghiệm sinh viên: nhóm, đăng ký, tiến độ, kết quả |
| Nguyễn Lê Hữu Chí | `frontend/src/pages/` cho quản trị/giảng viên; `frontend/src/features/notifications/`; API frontend tương ứng | Màn hình quản lý học vụ, tiêu chuẩn đánh giá, phân công, chấm điểm, thông báo và quản trị giao diện |
| Nguyễn Vũ Luân | `backend/src/main/java/com/example/qldetai/config/`, `security/`, `common/`, `system/`, `feature/identity/`, `feature/academic/`; `database/`, Docker và cấu hình môi trường | Nền tảng, xác thực/phân quyền, tài khoản, khoa/bộ môn/học kỳ, đợt đăng ký, schema/migration và triển khai |
| Phạm Hồng Anh | `backend/src/main/java/com/example/qldetai/feature/topic/`, `feature/groupregistration/`, `feature/project/`, `feature/communication/email/`; API/trang frontend về đề tài, nhóm và đăng ký | Đề tài, GVHD, nhóm, lời mời, đăng ký đề tài, giao đề tài, thông báo email |
| Phạm Hữu Ngọc | `backend/src/main/java/com/example/qldetai/feature/progress/`, `feature/evaluation/`; API/trang frontend về tiến độ, báo cáo, hội đồng, chấm điểm và kết quả | Mốc báo cáo, nộp tệp, phản hồi, hội đồng/bảo vệ, bộ tiêu chí, phiếu điểm, công bố và tra cứu kết quả |

## Nghiệp vụ và vị trí mã

| Nghiệp vụ | Backend | Frontend/route |
| --- | --- | --- |
| Tài khoản, đăng nhập, phân quyền | `feature/identity/`, `config/SecurityConfig.java` | `/login`, `/account`, `/admin-users/new` |
| Khoa, bộ môn, học kỳ, đợt đăng ký | `feature/academic/` | `/academic-management`, `/admin-appointments` |
| Đề tài, hướng dẫn, nhóm và đăng ký | `feature/topic/`, `feature/groupregistration/`, `feature/project/` | `/topics`, `/groups`, `/registrations` |
| Tiến độ và báo cáo | `feature/progress/` | `/progress` |
| Bộ tiêu chí, phản biện, hội đồng, chấm điểm | `feature/evaluation/` | `/evaluations`, `/evaluator-assignment`, `/score-sheets` |
| Công bố/tra cứu kết quả | `feature/evaluation/` | `/results` |
| Thông báo, email, nhật ký | `feature/communication/`, `feature/communication/email/`, `common/` | `/announcements`, `features/notifications/` |

## Quy ước bàn giao

- API thay đổi phải cập nhật DTO, test backend và module gọi API ở frontend.
- Migration/schema, Docker và dữ liệu demo do Nguyễn Vũ Luân rà soát trước khi merge.
- Mỗi thay đổi xuyên feature cần ghi rõ người phối hợp trong pull request hoặc nhật ký nhóm.
