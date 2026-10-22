# Hoàn thiện bảo mật và frontend — 22/10/2026

Thực hiện 7 mục, mỗi mục một commit. Không push, không deploy, không thay đổi schema hoặc dữ liệu production.

## Thay đổi

1. **Upload:** giới hạn file 20 MB và request 21 MB; lỗi vượt dung lượng trả HTTP 413 theo `ApiResponse.failure` với thông báo “Tệp quá lớn. Tối đa 20 MB.”
2. **File:** whitelist PDF, Office, ZIP, PNG, JPG/JPEG, TXT; đối chiếu MIME với đuôi file và kiểm tra signature nội dung. MIME rỗng/octet-stream chỉ được chấp nhận sau kiểm tra định dạng, rồi lưu MIME chuẩn. Chặn preview SVG, kể cả file cũ. Preview có CSP sandbox và nosniff; download cũng có nosniff.
3. **Đăng nhập:** tối đa 5 lần sai theo cặp username đã chuẩn hóa + IP trong cửa sổ trượt 15 phút; quá giới hạn trả 429 bằng tiếng Việt. Kiểm tra và cập nhật cùng cặp được tuần tự hóa, bộ đếm có TTL và giới hạn bộ nhớ. Thành công xóa số lần sai. Có test TTL, ranh giới cửa sổ, reset, tách cặp và request đồng thời.
4. **Mật khẩu:** hai DTO backend có cùng nhóm ký tự Unicode như frontend; sửa chính tả thông báo. BCrypt của phiên bản hiện tại từ chối encode trên 72 byte, nên dùng PBKDF2 cho mật khẩu mới để hỗ trợ giới hạn 100 ký tự. Encoder vẫn xác thực hash BCrypt cũ, có hoặc không có prefix. Không tự chuyển đổi hash đang lưu.
5. **Session:** sau khi transaction đổi mật khẩu commit, cập nhật trạng thái xác thực của phiên hiện tại. Những phiên còn giữ hash cũ bị trả 401 và invalidate ở request tiếp theo. Đổi thất bại không thu hồi phiên. Không thêm bảng hoặc dịch vụ session mới.
6. **Triển khai:** bỏ wildcard tunnel khỏi CORS mặc định; chỉ bật qua `CORS_ALLOWED_ORIGINS`. Nginx có CSP, DENY, Referrer-Policy và nosniff, bao gồm các location tự khai báo Cache-Control. Backend chạy UID/GID 10001; service khởi tạo quyền volume giữ nguyên nội dung file cũ. Compose yêu cầu mật khẩu MySQL khác rỗng. Demo có env riêng, chỉ dùng local.
7. **Frontend:** chạy `npm audit fix`, nâng mức Vite tối thiểu lên 8.3.5. Sửa hai nhóm cảnh báo hook được yêu cầu, bắt đầu ở autocomplete; giữ hủy kết quả cũ khi đổi lựa chọn. Trạng thái tải theo request key, reset form trước khi hiển thị dữ liệu mới và dependency ổn định. Không tắt rule lint.

## Kiểm tra

- Sau mỗi mục đã chạy backend `mvnw clean test`, frontend `typecheck`, `lint`, `test`.
- Backend cuối: **202 test đạt**, không có failure/error.
- Frontend: **119 test đạt**, TypeScript và build đạt; thêm **20 test utility đạt**.
- `npm audit`: **0 vulnerability**; Vite **8.3.5**.
- Hai nhóm `set-state-in-effect` và `exhaustive-deps`: **0 cảnh báo**.
- Lint không có lỗi; còn **5 cảnh báo ngoài hai nhóm yêu cầu**: `only-export-components` ở AuthContext/ToastContext, `purity` ở GroupsPage/EvaluatorAssignmentPage, `immutability` ở DashboardPage. Không mở rộng refactor các nhóm này trong đợt sửa.
- Browser mock API: **78 lượt đạt**, gồm vai trò quản lý, giảng viên, sinh viên và guest ở desktop/tablet/mobile; thêm **12 lượt đạt trên Nginx image thật có CSP**. Không ghi dữ liệu ứng dụng.
- Docker image frontend và backend build đạt. Kiểm tra Nginx header ở HTML, route SPA, asset; backend UID 10001 đọc file cũ và ghi file mới trên volume tạm đạt. Container/volume kiểm thử đã được xóa.
- Compose demo parse đạt. Compose chính từ chối cấu hình thiếu/rỗng mật khẩu bắt buộc. Không chạy `compose up` trên stack hiện tại.

## Kiểm tra thủ công trước khi đưa lên VPS

1. Upload các báo cáo thực tế ở từng định dạng whitelist; thử file giả đuôi/MIME, SVG và file vượt 20 MB. Kiểm tra preview PDF/ảnh/TXT, tải Office/ZIP và quyền truy cập file giữa các nhóm.
2. Thử 5 lần sai rồi lần thứ 6 trên cùng tài khoản/IP, kiểm tra 429, reset khi đăng nhập đúng trước giới hạn và hết cửa sổ 15 phút. Xác nhận reverse proxy cung cấp IP tin cậy: backend đang dùng địa chỉ đã được cơ chế forwarded-header hiện có giải quyết, chưa thay đổi chính sách tin cậy chuỗi proxy. Bộ đếm nằm trong từng tiến trình và reset khi khởi động lại.
3. Kiểm tra đăng nhập tài khoản BCrypt cũ; tạo/đổi mật khẩu Unicode và mật khẩu dài, đồng thời thử mật khẩu thiếu các nhóm ký tự.
4. Dùng hai trình duyệt hoặc phiên ẩn danh độc lập: đổi mật khẩu ở một phiên, phiên đó vẫn dùng được; phiên kia phải đăng nhập lại khi gửi request tiếp theo.
5. Khai báo `MYSQL_ROOT_PASSWORD`, `MYSQL_APP_PASSWORD`, CORS cần dùng và kiểm tra quyền file sau khi chuyển sang user 10001. Bước init volume chỉ đổi chủ sở hữu, không xóa nội dung; cần được tính vào quy trình triển khai hiện có.
6. Kiểm tra CSP với trang đăng nhập, form, xem trước báo cáo và mẫu email. Nếu có tài nguyên ngoài domain đang dùng thực tế, bổ sung nguồn cụ thể sau khi xác minh; không mở wildcard mặc định.

Mật khẩu mới có prefix `{pbkdf2}`. Khi rollback, backend vẫn cần giữ khả năng đọc PBKDF2; phiên bản cũ chỉ hỗ trợ BCrypt không xác thực được hash mới. Không có migration dữ liệu tự động trong thay đổi này.

Demo local dùng project/volume riêng:

```powershell
docker compose --env-file docker-compose.demo.env -p qldetai-demo -f docker-compose.yml -f docker-compose.demo.yml up --build
```

`docker-compose.demo.env` chứa giá trị công khai cho demo, không dùng trên VPS. Nếu đang chạy một stack local khác, chọn các port khác trước khi khởi động demo.

## File thay đổi

- `backend/Dockerfile`
- `backend/src/main/java/com/example/qldetai/config/SecurityConfig.java`
- `backend/src/main/java/com/example/qldetai/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/example/qldetai/exception/TooManyRequestsException.java`
- `backend/src/main/java/com/example/qldetai/feature/identity/controller/AuthController.java`
- `backend/src/main/java/com/example/qldetai/feature/identity/dto/IdentityDtos.java`
- `backend/src/main/java/com/example/qldetai/feature/identity/service/AuthService.java`
- `backend/src/main/java/com/example/qldetai/feature/progress/controller/StoredFileController.java`
- `backend/src/main/java/com/example/qldetai/feature/progress/service/StoredFileService.java`
- `backend/src/main/java/com/example/qldetai/security/CurrentUserRefreshFilter.java`
- `backend/src/main/java/com/example/qldetai/security/LoginAttemptLimiter.java`
- `backend/src/main/resources/application.properties`
- `backend/src/test/java/com/example/qldetai/config/CorsConfigurationTests.java`
- `backend/src/test/java/com/example/qldetai/exception/GlobalExceptionHandlerTests.java`
- `backend/src/test/java/com/example/qldetai/feature/identity/AuthIntegrationTests.java`
- `backend/src/test/java/com/example/qldetai/feature/identity/AuthSessionIntegrationTests.java`
- `backend/src/test/java/com/example/qldetai/feature/identity/LoginAttemptLimiterTests.java`
- `backend/src/test/java/com/example/qldetai/feature/identity/PasswordEncoderIntegrationTests.java`
- `backend/src/test/java/com/example/qldetai/feature/identity/PasswordPolicyTests.java`
- `backend/src/test/java/com/example/qldetai/feature/progress/StoredFileIntegrationTests.java`
- `backend/src/test/java/com/example/qldetai/feature/progress/StoredFileSecurityTests.java`
- `docker-compose.demo.env`
- `docker-compose.demo.yml`
- `docker-compose.yml`
- `docs/security-hardening-2026-10-22.md`
- `frontend/nginx.conf`
- `frontend/package-lock.json`
- `frontend/package.json`
- `frontend/src/components/DefenseSchedulePanel.tsx`
- `frontend/src/components/EditAccountForm.tsx`
- `frontend/src/components/LecturerAutocomplete.tsx`
- `frontend/src/context/AuthContext.tsx`
- `frontend/src/features/notifications/components/NotificationMenu.tsx`
- `frontend/src/hooks/useRequestLoading.ts`
- `frontend/src/pages/AcademicAppointmentsPage.tsx`
- `frontend/src/pages/AcademicManagementPage.tsx`
- `frontend/src/pages/AnnouncementsPage.tsx`
- `frontend/src/pages/AssessmentSetupWizardPage.tsx`
- `frontend/src/pages/DashboardPage.tsx`
- `frontend/src/pages/EmailSettingsPage.tsx`
- `frontend/src/pages/EvaluationsPage.tsx`
- `frontend/src/pages/EvaluatorAssignmentPage.tsx`
- `frontend/src/pages/GroupsPage.tsx`
- `frontend/src/pages/MilestoneManagementPage.tsx`
- `frontend/src/pages/RegistrationsPage.tsx`
- `frontend/src/pages/StudentProgressPage.tsx`
- `frontend/src/pages/StudentResultsPage.tsx`
- `frontend/src/pages/TopicsPage.tsx`
- `frontend/src/pages/UserManagementPage.tsx`
- `frontend/src/utils/passwordPolicy.ts`
- `frontend/tests/hooksHardening.test.tsx`
- `frontend/tests/passwordPolicy.test.tsx`
