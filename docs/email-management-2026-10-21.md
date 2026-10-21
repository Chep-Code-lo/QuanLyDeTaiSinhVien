# Bổ sung quản lý và gửi email — 21/10/2026

## Phạm vi đã hoàn thành

- Giảng viên có mục **Cấu hình email**, bên cạnh quản trị viên.
- Quản trị viên gửi theo nhóm Sinh viên, Giảng viên hoặc chọn từng người. Giảng viên chỉ gửi theo nhóm Sinh viên hoặc chọn từng sinh viên đang hướng dẫn. Không tự thêm tài khoản đăng nhập vào người nhận.
- Giảng viên chọn sinh viên thuộc nhóm có đăng ký đề tài APPROVED mà mình hướng dẫn, không gửi cho giảng viên khác. Thành viên đã rời nhóm, đăng ký PENDING/REJECTED/WITHDRAWN và tài khoản không khả dụng không được chọn. Giới hạn này áp dụng cho cả tìm kiếm, nhóm và ID cụ thể ở backend.
- Có **Tạo mẫu mới**, tên mẫu, lưu, mở lại, chỉnh sửa và gửi mẫu riêng. Mỗi tài khoản quản lý mẫu của mình; danh sách có phân trang. Mẫu riêng phục vụ gửi thủ công, không tạo thêm sự kiện tự động.
- Đã bỏ khối SMTP, hướng dẫn sử dụng, danh sách biến và nút gửi thử về tài khoản đăng nhập trên giao diện theo yêu cầu.

## Bằng chứng và thay đổi

| File / phương thức | Lỗi hoặc thiếu sót ban đầu | Thay đổi | Mức độ | Kiểm thử |
|---|---|---|---|---|
| `navigation.ts`, `AppRoutes.tsx`, `EmailSettingsPage.tsx` | Menu và route email chỉ cho ADMIN; UI chỉ có mẫu sự kiện cố định | Mở cho LECTURER; dùng scope cá nhân; thêm mẫu riêng, tạo/sửa/gửi | Trung bình | Route, menu, phạm vi request và luồng tạo/mở lại/sửa/gửi cho cả hai vai trò |
| `EmailDispatchService`, `EmailRecipientAccess`, `UserRepository` | Gửi thủ công chỉ hỗ trợ quản trị viên, chưa có phạm vi người nhận của giảng viên | Cùng một Specification lọc DB cho tìm kiếm, nhóm và ID cụ thể; nhóm được xử lý theo từng trang; từ chối toàn bộ yêu cầu nếu có ID ngoài phạm vi | Cao | Nhóm sinh viên/giảng viên, tìm kiếm, phân trang, chống vượt quyền qua ID, không xếp hàng một phần |
| `EmailTemplate*` | Chưa lưu được mẫu mới ngoài cấu hình mặc định | Bảng riêng gắn chủ sở hữu, CRUD tạo/đọc danh sách/cập nhật, validation dùng lại thành phần hiện có; `@Version` chống ghi đè | Trung bình | Lưu và đọc lại DB, phân trang, quyền sở hữu, phiên bản cũ trả conflict, gửi nội dung mẫu riêng |
| `EmailLogService.list`, `EmailOutboxRepository` | Nhánh trưởng khoa chỉ lấy email khoa, bỏ sót email thuộc scope giảng viên của chính mình | Bao gồm email scope LECTURER của tài khoản hiện tại | Trung bình | Trưởng khoa thấy email mình gửi trong danh sách và chi tiết |
| `User.replaceRoles`, `UserRole` | MySQL báo trùng `uq_user_roles_01` khi xóa rồi tạo lại liên kết vai trò còn giữ; H2 thiếu ánh xạ unique nên bỏ sót | Giữ liên kết không thay đổi, chỉ thêm/bỏ vai trò cần thiết; ánh xạ unique vốn đã có trong SQL | Cao | MySQL tái hiện lỗi trước sửa; sau sửa kiểm tra thêm vai trò, cập nhật lặp lại, bỏ vai trò và lọc người nhận tương ứng |
| `003-email-templates.sql`, schema bootstrap | Chưa có nơi lưu mẫu cá nhân | Tạo bảng `email_templates`, khóa ngoại owner, index theo owner/thời gian, body MEDIUMTEXT | Trung bình | MySQL chạy migration nhiều lần, cấu hình và mẫu có sẵn còn nguyên |
| `EmailRecipientAccess`, `EmailDispatchService`, `EmailSettingsPage` | Phạm vi giảng viên ban đầu gồm đồng nghiệp cùng khoa | Theo yêu cầu mới, chỉ cho sinh viên đang hướng dẫn; bỏ lựa chọn giảng viên và chặn role/ID ở backend | Cao | Kiểm tra tìm kiếm, gửi theo nhóm và cố gửi ID đồng nghiệp |
| `EvaluationResultService.publish`, `EmailNotificationEventService`, `EmailEventContextFactory`, `EmailTemplateCatalog` | Công bố điểm chỉ có thông báo trong ứng dụng | Thêm email RESULT_PUBLISHED, nội dung điểm và thành viên theo bản kết quả đã chốt, idempotency theo ID kết quả | Cao | Công bố tạo thư, chưa công bố/tắt cấu hình không tạo thư, nội dung đúng, snapshot thành viên và chống lặp; đã thử cả MySQL |
| `004-result-published-email.sql`, schema chính | CHECK chỉ nhận năm sự kiện cũ | Thêm RESULT_PUBLISHED cho cấu hình và outbox, không thay dòng dữ liệu | Trung bình | Nâng cấp từ CHECK cũ, chạy lại, xác nhận cấu hình/thư cũ vẫn nguyên |

## Tương thích và migration

Không đổi tên bảng/cột cũ, payload hay cấu trúc `ApiResponse` của endpoint đang có. Các mẫu mặc định và thông báo tự động tiếp tục dùng cấu hình hiện tại. Endpoint `/notification-settings/{eventType}/test` được giữ để tương thích với client cũ; giao diện đã chuyển sang gửi theo người nhận được chọn.

Các endpoint bổ sung (prefix `/api/v1`, ADMIN hoặc LECTURER):

| Method | Endpoint | Chức năng |
|---|---|---|
| GET | `/email-recipients?role=&search=&page=0&size=20` | Người nhận được phép truy cập, có phân trang |
| POST | `/notification-settings/{eventType}/send` | Gửi mẫu mặc định đã lưu trong scope của người gửi |
| GET | `/email-templates?page=0&size=20` | Mẫu do tài khoản hiện tại tạo |
| POST | `/email-templates` | Tạo mẫu |
| PUT | `/email-templates/{id}` | Cập nhật mẫu của mình; cần version hiện tại |
| POST | `/email-templates/{id}/send` | Gửi mẫu riêng của mình |

Body gửi thư: `{"recipientRoles":["STUDENT"],"recipientUserIds":[]}` hoặc danh sách ID được chọn; response data là `{"recipientCount":N}`. Không nhận scope người gửi từ client. CSRF và xác thực hiện có vẫn áp dụng.

Áp dụng riêng `database/migrations/003-email-templates.sql` trên database đang có dữ liệu trước khi chạy backend mới. Docker Compose chạy file này qua `schema-migrate` khi dựng lại. File chỉ `CREATE TABLE IF NOT EXISTS`, không UPDATE/DELETE dữ liệu cũ. Không chạy lại toàn bộ schema bootstrap trên database có dữ liệu. Kiểm thử migration dùng MySQL container dùng một lần, tách khỏi database ứng dụng.

## Bổ sung email kết quả điểm

Trước thay đổi này, `EvaluationResultService.publish` chỉ tạo thông báo trong ứng dụng. Nay sự kiện `RESULT_PUBLISHED` tạo email qua transactional outbox trong cùng transaction công bố. Không gửi khi mới lưu phiếu hoặc chốt kết quả chưa công bố. Người nhận lấy từ `result_members` đã chốt; không lấy danh sách thành viên hiện tại để tránh gửi sai người nếu nhóm thay đổi. Event key chứa ID phiên bản kết quả và unique outbox hiện có ngăn xếp hàng lặp cho cùng người/sự kiện.

Quản trị viên vào **Cấu hình email → Công bố kết quả điểm**, bật và lưu mẫu. Mẫu mặc định gồm tên sinh viên, đề tài, điểm tổng kết, số phiên bản, thời gian công bố và đường dẫn `/results` đang có trên frontend. Chỉ nhóm DIRECT_PARTICIPANTS được phép nhận; cấu hình gửi điểm cho toàn khoa bị từ chối. Biểu mẫu không có chọn người nhận/nút gửi thủ công cho loại này, backend cũng từ chối gửi thủ công `RESULT_PUBLISHED`. SMTP tiếp tục được cấu hình bằng môi trường và worker hiện có; cấu hình mặc định vẫn tắt để quản trị viên chủ động bật.

Database cũ cần chạy `database/migrations/004-result-published-email.sql`: mở rộng hai CHECK `event_type` của bảng cấu hình/outbox, giữ nguyên các giá trị cũ. Mỗi CHECK được thay bằng một ALTER nguyên tử, có thể chạy lại, không sửa/xóa các dòng dữ liệu. SQL chính đã cập nhật tương ứng cho database mới; dữ liệu demo vẫn tách riêng. Không chạy lại schema chính trên database đã có dữ liệu.

Kiểm thử bổ sung: chặn giảng viên gửi cho đồng nghiệp bằng role và ID; tạo email khi công bố; không tạo trước công bố hoặc khi tắt cấu hình; nội dung điểm và sinh viên chính xác; dùng snapshot thành viên khi nhóm thay đổi; xử lý lặp không tạo thư trùng; UI mẫu điểm chỉ cấu hình gửi tự động. Kết quả chạy cuối được ghi ở phần cập nhật bên dưới.

Email thủ công tiếp tục dùng outbox, sender allow-list, renderer văn bản thường, cơ chế retry và nhật ký hiện có. Để giữ enum/check constraint cũ, mẫu riêng dùng loại TASK_ASSIGNED nội bộ và event key `email-manual:<UUID>`; UI nhật ký hiển thị **Email thủ công**. Không phát sinh sự kiện giao nhiệm vụ trong nghiệp vụ.

## Kiểm thử trước bổ sung email điểm và giới hạn

| Lệnh / kiểm tra | Kết quả thực tế |
|---|---|
| `backend/mvnw.cmd -DskipTests compile` | BUILD SUCCESS |
| `backend/mvnw.cmd test` (lần cuối, gồm compile) | **119 test**, 0 lỗi, 0 thất bại, 0 bỏ qua; BUILD SUCCESS |
| `backend/mvnw.cmd -Dtest=EmailDispatchIntegrationTests -Demail.test.mysql.url=<MySQL dùng một lần> test` | **21 test** trên MySQL 8.4.10, 0 lỗi, 0 thất bại, 0 bỏ qua |
| `frontend: npm test` | **73 test**, 3 file, tất cả qua |
| `frontend: npm run build` | TypeScript và Vite build thành công |
| Migration 003 trên MySQL riêng | Chạy nhiều lần thành công; subject/body cấu hình cũ và name/body mẫu cũ không đổi |
| `git diff --check` | Không có lỗi khoảng trắng; Git chỉ nhắc chuẩn hóa LF/CRLF ở file API frontend |

Không bật SMTP hoặc gửi thư thật trong quá trình kiểm thử. Container MySQL dùng một lần đã dừng và tự xóa; không sửa database ứng dụng đang chạy.

Hibernate schema validation toàn cục trên MySQL dừng tại khác biệt kiểu có sẵn `assessment_components.expected_evaluators` (SQL TINYINT, Java Integer). Không thay schema nghiệp vụ vì các giá trị bị giới hạn và cơ chế triển khai hiện dùng `ddl-auto=none`. Kiểm thử MySQL email dùng chính schema SQL với `ddl-auto=none`; không coi đây là một lần Hibernate schema validation thành công.

Hai lần bấm gửi riêng biệt được xem là hai yêu cầu gửi; không tự gộp giữa các request. Trong một yêu cầu, địa chỉ trùng giữa nhóm và lựa chọn cụ thể chỉ được xếp hàng một lần. Việc giao thư thực tế vẫn phụ thuộc SMTP của môi trường triển khai; test chỉ xác minh xếp hàng, nội dung, quyền truy cập và log.

## Kết quả cuối sau bổ sung email điểm

| Kiểm tra | Kết quả |
|---|---|
| `mvnw.cmd clean test` | Biên dịch lại 234 file production, 20 file test; **125 test qua**, 0 lỗi/thất bại/bỏ qua; BUILD SUCCESS |
| MySQL 8.4.10 — `EmailDispatchIntegrationTests` | **23 test qua**, gồm chặn giảng viên gửi cho đồng nghiệp qua role và ID |
| MySQL 8.4.10 — 3 test mới trong `ProjectLifecycleIntegrationTests` | **3 test qua**: công bố điểm/chống lặp, tắt cấu hình, snapshot thành viên |
| `npm test` | **74 test qua**, 3 file |
| `npm run build` | TypeScript và Vite build thành công |
| Migration 004 | Nâng cấp từ CHECK cũ thành công; chạy lại được; cấu hình TASK_ASSIGNED và email PENDING cũ giữ nguyên nội dung/trạng thái |
| Compose mặc định và override demo, `git diff --check` | Cấu hình hợp lệ; không có lỗi khoảng trắng |

Fixture test MySQL được chuyển tuần tự từ giai đoạn đề xuất sang đăng ký để tuân thủ CHECK thời gian hiện có; không nới CHECK hoặc thay quy tắc nghiệp vụ. Không gửi SMTP thật, không áp dụng migration lên database ứng dụng đang chạy. Container MySQL kiểm thử đã được dừng và tự xóa. Để sử dụng trên database cũ, áp dụng migration 004, cập nhật ứng dụng rồi bật/lưu mẫu Công bố kết quả điểm và cấu hình SMTP.
