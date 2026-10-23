# Rà soát phân quyền, upload và lịch bảo vệ — 23/10/2026

## Phạm vi và trạng thái nguồn

- Nhánh làm việc: `main`. Đã fetch và đối chiếu `HEAD...origin/main`: `0 0` khi bắt đầu.
- Giữ nguyên các thay đổi chưa commit về yêu cầu đổi điểm, thành phần hội đồng và xem trước điểm đã có từ các lượt trước.
- Không commit, push, merge, chạy SQL sửa dữ liệu thật, xóa volume hoặc thêm migration.
- Không đổi URL hay cấu trúc request/response HTTP trong đợt rà soát này. Một số service nội bộ nhận thêm `actorId` để tự xác minh quyền.
- Không phát hiện P0 trong phạm vi đã kiểm tra. Đây không phải cam kết hệ thống không còn lỗ hổng.

## Lỗi có bằng chứng và cách khắc phục

| Mã | Mức độ | Lỗi, nguyên nhân | Thay đổi |
|---|---|---|---|
| AUTH-01 | P1 | Tạo/sửa/chuyển trạng thái đợt đăng ký có ngoại lệ `hasRole('ADMIN')`; service không tự xác minh bổ nhiệm. Admin thuần có thể ghi học thuật. | Bỏ ngoại lệ Admin ở cả ba endpoint; service yêu cầu DEAN còn hiệu lực của đúng khoa. Update/status bắt buộc truyền actor. |
| AUTH-02 | P1 | Tạo/khóa thành phần và rubric theo đợt cũng có ngoại lệ Admin; service chỉ kiểm tra nhóm vai trò staff. | Controller và service đều yêu cầu quản lý đúng đợt/khoa. Admin thuần vẫn xem giám sát, không tạo hoặc khóa thay Trưởng khoa. |
| AUTH-03 | P1 | Tạo/sửa mốc và gửi nhắc hạn có ngoại lệ Admin; update/reminder service thiếu actor nên không tự kiểm tra phạm vi đề tài. | Bỏ ngoại lệ; bổ sung kiểm tra bộ môn/khoa ở create/update/reminder service và truyền actor từ principal. Nhận xét vẫn chỉ dành cho GVHD thực tế. |
| AUTH-04 | P1 | Hồ sơ giảng viên/bổ nhiệm còn lưu sau khi mất vai trò LECTURER vẫn có thể được dùng để xem báo cáo, tệp hướng dẫn hoặc tiêu chí nháp. | Kiểm tra vai trò hiện tại trước quyền hướng dẫn/nhận xét; nhánh đọc file yêu cầu LECTURER cho GVHD; quyền đọc tiêu chí nháp xác minh vai trò hiện tại cùng bổ nhiệm còn hiệu lực. |
| FILE-01 | P2 | Chỉ có giới hạn từng file; không có quota, giới hạn upload theo phút hay số file chưa gắn nghiệp vụ cho từng người. | Bổ sung quota và giới hạn cấu hình được; khóa hàng người dùng trước khi đếm/ghi để các request đồng thời không vượt quota. Kiểm tra cả số byte thực đọc, không tin riêng metadata multipart. |
| FILE-02 | P2 | Khi upload thành công nhưng nộp báo cáo lỗi, mỗi lần thử lại upload thêm một file mới. | Giữ ID file đã upload cho chính đối tượng File đang chọn và dùng lại khi thử nộp. Đổi tác vụ/file không tái sử dụng nhầm tệp khác. |
| DEF-01 | P2 | Controller yêu cầu quyền quản lý đề tài và hội đồng, nhưng service schedule chỉ kiểm tra quyền bộ môn; eligibility không tự kiểm tra cả hai. | Eligibility và schedule đều xác minh quyền quản lý đề tài cùng quyền quản lý đợt của hội đồng. Trưởng bộ môn không tự được thực hiện quyền DEAN. Đây là bổ sung bảo vệ service, không phải tuyên bố controller cũ đã cho phép khai thác qua HTTP. |
| UI-01 | P2 | Giao diện quản lý đợt dựa vào `isAdmin || isDean`, chưa giới hạn từng dòng theo khoa; AuthProvider chỉ tải quyền ban đầu. | Nút tạo/chuyển trạng thái theo khoa của bổ nhiệm hiện tại; tiêu chí/mốc chỉ mở thao tác quản lý khi backend cho phép. Làm mới user khi focus/hiển thị lại trang và mỗi 60 giây khi trang đang hiển thị. Backend vẫn kiểm tra ngay từng request. |
| UI-02 | P3 | ProtectedRoute và Sidebar có ngoại lệ Admin cho điều kiện chức vụ. Các route hiện tại chủ yếu đã dùng guard riêng, nhưng component vẫn có hành vi kế thừa sai nếu tái sử dụng. | Bỏ ngoại lệ; test trực tiếp route yêu cầu DEAN với Admin thuần. Không chặn các route giám sát được phép của Admin. |
| FILE-03 | P3 | Tên file có ký tự điều khiển vẫn được chấp nhận. | Từ chối trước khi ghi metadata/nội dung. Không tuyên bố đã xác nhận khai thác header injection. |

## Vị trí code chính

Các số dòng bên dưới trỏ đến vị trí kiểm tra trong bản sửa, có thể thay đổi sau khi tiếp tục phát triển.

- AUTH-01: [RegistrationPeriodController.java:57](../backend/src/main/java/com/example/qldetai/feature/academic/controller/RegistrationPeriodController.java#L57), [RegistrationPeriodService.java:88](../backend/src/main/java/com/example/qldetai/feature/academic/service/RegistrationPeriodService.java#L88).
- AUTH-02: [AssessmentComponentController.java:45](../backend/src/main/java/com/example/qldetai/feature/evaluation/controller/AssessmentComponentController.java#L45), [RubricController.java:45](../backend/src/main/java/com/example/qldetai/feature/evaluation/controller/RubricController.java#L45), [EvaluationService.java:123](../backend/src/main/java/com/example/qldetai/feature/evaluation/service/EvaluationService.java#L123).
- AUTH-03: [ProgressController.java:30](../backend/src/main/java/com/example/qldetai/feature/progress/controller/ProgressController.java#L30), [ProgressService.java:60](../backend/src/main/java/com/example/qldetai/feature/progress/service/ProgressService.java#L60), [MilestoneReminderService.java:53](../backend/src/main/java/com/example/qldetai/feature/progress/service/MilestoneReminderService.java#L53).
- AUTH-04: [EvaluationCatalogAccess.java:61](../backend/src/main/java/com/example/qldetai/feature/evaluation/service/EvaluationCatalogAccess.java#L61), [ProgressService.java:146](../backend/src/main/java/com/example/qldetai/feature/progress/service/ProgressService.java#L146), [StoredFileAuthorization.java:88](../backend/src/main/java/com/example/qldetai/feature/progress/service/StoredFileAuthorization.java#L88).
- FILE-01/03: [StoredFileService.java:75](../backend/src/main/java/com/example/qldetai/feature/progress/service/StoredFileService.java#L75), [StoredFileRepository.java](../backend/src/main/java/com/example/qldetai/feature/progress/repository/StoredFileRepository.java), [application.properties](../backend/src/main/resources/application.properties).
- FILE-02: [StudentProgressPage.tsx:40](../frontend/src/pages/StudentProgressPage.tsx#L40).
- DEF-01: [DefenseSessionService.java:131](../backend/src/main/java/com/example/qldetai/feature/evaluation/service/DefenseSessionService.java#L131) và kiểm tra eligibility tại dòng 290.
- UI-01: [AcademicManagementPage.tsx:61](../frontend/src/pages/AcademicManagementPage.tsx#L61), [AssessmentSetupWizardPage.tsx:480](../frontend/src/pages/AssessmentSetupWizardPage.tsx#L480), [MilestoneManagementPage.tsx:249](../frontend/src/pages/MilestoneManagementPage.tsx#L249), [AuthProvider.tsx:25](../frontend/src/context/AuthProvider.tsx#L25).
- UI-02: [ProtectedRoute.tsx:68](../frontend/src/components/ProtectedRoute.tsx#L68), [Sidebar.tsx:49](../frontend/src/components/Sidebar.tsx#L49).

## Quyền sau sửa

| Thao tác | Admin thuần | DEAN còn hiệu lực | DEPARTMENT_HEAD còn hiệu lực | Giảng viên thường | Sinh viên |
|---|---|---|---|---|---|
| Quản trị tài khoản, bổ nhiệm, email, nhật ký | Có | Không mặc định | Không | Không | Không |
| Danh mục hệ thống: khoa, bộ môn, học kỳ | Giữ quyền quản trị | Theo quyền giới hạn hiện có | Theo phạm vi hiện có | Không | Không |
| Xem thông tin học thuật | Giám sát | Theo phạm vi | Theo phạm vi | Liên quan/phân công | Nhóm và quyền của mình |
| Tạo/sửa/bật đợt, cấu hình/khóa tiêu chí theo đợt | Không | Đúng khoa | Không mặc định | Không | Không |
| Quản lý mốc đề tài | Không | Đúng khoa | Đúng bộ môn | Không mặc định | Không |
| Nhận xét báo cáo | Không | Khi là GVHD thực tế | Khi là GVHD thực tế | GVHD thực tế | Không |
| Phân công phản biện | Không | Đúng khoa | Đúng bộ môn theo cơ chế hiện có | Không | Không |
| Hội đồng/xếp lịch | Không | Đúng khoa và đợt | Không mặc định | Không | Không |
| Chấm điểm | Không | Khi được phân công | Khi được phân công | Khi được phân công | Không |
| Chốt có hội đồng | Không mặc định | Khi là đúng Chủ tịch | Khi là đúng Chủ tịch | Khi là đúng Chủ tịch | Không |
| Công bố | Không | Đúng thẩm quyền/phạm vi hiện có | Theo thẩm quyền hiện có | Không | Không |

ADMIN, LECTURER và STUDENT là role thực tế. DEAN, DEPARTMENT_HEAD là chức vụ bổ nhiệm. Tài khoản nhiều vai trò được xét từng quyền và từng đối tượng; ADMIN không thay thế bổ nhiệm.

## Những cơ chế đã kiểm tra và giữ nguyên

- `SecurityConfig` dùng method security, CSRF, session và `CurrentUserRefreshFilter`; filter tải lại role/trạng thái user từng request, không dùng role cũ trong phiên làm thẩm quyền ghi.
- AcademicAuthorization/TopicAuthorization/ProjectAuthorization/RegistrationAuthorization/EvaluationAuthorization giữ kiểm tra khoa/bộ môn, thời hạn và đối tượng liên quan. Không gộp role thành một quyền toàn năng.
- Giữ cơ chế chấm theo phân công, hạn chấm, trạng thái ACTIVE, immutable result snapshot, kiểm soát phiên bản điểm, audit và quy trình đổi điểm.
- Giữ khóa lịch bảo vệ, khóa hàng project/committee, kiểm tra giao thời gian phòng/hội đồng/giảng viên, ngày KLTN theo lịch Việt Nam, phản biện hoàn tất và cấm tạo hai phiên cho cùng đề tài.
- Giữ kiểm tra đường dẫn lưu trữ, allowlist MIME/extension và chữ ký file, giới hạn byte thực, `nosniff`, CSP khi preview, quyền tải theo nghiệp vụ và cleanup rollback. Không viết lại cơ chế lưu file.
- Upload hai bước vẫn được hỗ trợ: upload trước, sau đó gắn file của chính người gửi vào báo cáo/thông báo. Không đổi contract để bắt buộc thêm context chưa có trên client.
- Đường dẫn JPA `EvaluationAssignment.period` đã được kiểm tra với entity thực tế và là hợp lệ; không thay đổi đường dẫn này.

## Quota upload

- `app.files.user-quota` / `FILE_USER_QUOTA`: mặc định 500 MB nội dung READY mỗi tài khoản.
- `app.files.max-uploads-per-minute` / `FILE_UPLOADS_PER_MINUTE`: mặc định 10 upload thành công mỗi phút mỗi tài khoản; vượt giới hạn trả 429 bằng handler hiện có.
- `app.files.max-unattached-per-user` / `FILE_MAX_UNATTACHED_PER_USER`: mặc định 20 file READY chưa gắn báo cáo/thông báo.
- Dữ liệu đếm từ database, không dùng bộ đếm RAM riêng từng instance. Giới hạn tần suất này không phải rate limiter cho toàn bộ lưu lượng HTTP hoặc request upload thất bại trước bước lưu.
- Không tự động xóa file cũ. Nếu dữ liệu hiện có đã vượt giới hạn, cần rà soát chính sách quota hoặc xử lý file tồn theo quy trình được duyệt.

## Regression tests mới/cập nhật

- `RegistrationPeriodServiceTests.periodWritesRequireAnActiveDeanAndRefreshAuthorityWithinTheSameSession`: Admin thuần bị từ chối ở service và HTTP, DEAN đúng khoa được phép, ngoài khoa/hết hạn/thu hồi chức vụ hoặc role không được phép; dùng lại phiên đăng nhập cũ.
- `ProjectLifecycleIntegrationTests.pureAdminAndOrdinaryLecturerCannotMakeAcademicDecisions`: bổ sung tạo/khóa tiêu chí và tạo mốc, giữ kiểm tra phân công/chấm/chốt/công bố.
- `ProjectLifecycleIntegrationTests.departmentHeadDoesNotGainDeanSchedulingAuthorityOrManageAnotherDepartment`: ngoài bộ môn và không được nâng lên quyền quản lý hội đồng.
- `ProjectLifecycleIntegrationTests.revokedLecturerRoleRemovesSupervisionFilesAndDraftCatalogAccess`: mất LECTURER không giữ quyền qua hồ sơ/bổ nhiệm cũ; thử lại HTTP file bằng principal cũ.
- `StoredFileQuotaIntegrationTests`: hai upload chạy đồng thời chỉ một thành công khi tổng byte vượt quota; rate limit theo từng người, người khác không bị ảnh hưởng.
- `StoredFileSecurityTests`: quota/rate/file chưa gắn bị chặn trước khi ghi; metadata dung lượng giả không vượt quota; tên chứa control characters bị từ chối. Giữ các test định dạng, path và rollback có sẵn.
- `MilestoneReminderServiceTests`: cập nhật actor và xác nhận gọi kiểm tra phạm vi trước khi gửi.
- `authRefresh.test.tsx`: cập nhật chức vụ khi quay lại cửa sổ; Admin không vượt route yêu cầu DEAN.
- `registration-period.test.tsx`: Admin chỉ xem, DEAN không chuyển trạng thái đợt ngoài khoa. Fixture có role, khoa và thời gian bổ nhiệm thực tế.
- `studentProgressCalendar.test.tsx`: retry báo cáo dùng lại cùng file ID, không upload lặp.
- Các test scope, query count, ngày bảo vệ, xung đột phòng/giảng viên/hội đồng, quyền nhóm sinh viên, nhiều vai trò, phiên đăng nhập cũ và concurrency hiện có tiếp tục được chạy. Một lookup role mới cho rubric được tính riêng; dữ liệu rubric/criterion vẫn tải theo batch.

## Danh sách tệp nhận sửa trong lượt rà soát

Hai tệp EvaluationService và ProjectLifecycleIntegrationTests còn chứa thay đổi từ các lượt trước. Các tệp chấm điểm/xem trước chỉ có thay đổi từ trước không được liệt kê như bản sửa mới.

- `backend/src/main/java/com/example/qldetai/feature/academic/controller/RegistrationPeriodController.java`
- `backend/src/main/java/com/example/qldetai/feature/academic/service/RegistrationPeriodService.java`
- `backend/src/main/java/com/example/qldetai/feature/evaluation/controller/AssessmentComponentController.java`
- `backend/src/main/java/com/example/qldetai/feature/evaluation/controller/RubricController.java`
- `backend/src/main/java/com/example/qldetai/feature/evaluation/service/DefenseSessionService.java`
- `backend/src/main/java/com/example/qldetai/feature/evaluation/service/EvaluationCatalogAccess.java`
- `backend/src/main/java/com/example/qldetai/feature/evaluation/service/EvaluationService.java`
- `backend/src/main/java/com/example/qldetai/feature/progress/controller/ProgressController.java`
- `backend/src/main/java/com/example/qldetai/feature/progress/repository/StoredFileRepository.java`
- `backend/src/main/java/com/example/qldetai/feature/progress/service/MilestoneReminderService.java`
- `backend/src/main/java/com/example/qldetai/feature/progress/service/ProgressService.java`
- `backend/src/main/java/com/example/qldetai/feature/progress/service/StoredFileAuthorization.java`
- `backend/src/main/java/com/example/qldetai/feature/progress/service/StoredFileService.java`
- `backend/src/main/resources/application.properties`
- `backend/src/test/java/com/example/qldetai/feature/academic/RegistrationPeriodServiceTests.java`
- `backend/src/test/java/com/example/qldetai/feature/progress/MilestoneReminderServiceTests.java`
- `backend/src/test/java/com/example/qldetai/feature/progress/StoredFileSecurityTests.java`
- `backend/src/test/java/com/example/qldetai/workflow/BackendConsistencyIntegrationTests.java`
- `backend/src/test/java/com/example/qldetai/workflow/ProjectLifecycleIntegrationTests.java`
- `frontend/src/components/ProtectedRoute.tsx`
- `frontend/src/components/Sidebar.tsx`
- `frontend/src/context/AuthProvider.tsx`
- `frontend/src/pages/AcademicManagementPage.tsx`
- `frontend/src/pages/AssessmentSetupWizardPage.tsx`
- `frontend/src/pages/MilestoneManagementPage.tsx`
- `frontend/src/pages/StudentProgressPage.tsx`
- `frontend/tests/frontend.test.tsx`
- `frontend/tests/registration-period.test.tsx`
- `frontend/tests/studentProgressCalendar.test.tsx`
- `backend/src/test/java/com/example/qldetai/feature/progress/StoredFileQuotaIntegrationTests.java`
- `docs/security-review-2026-10-23.md`
- `frontend/tests/authRefresh.test.tsx`

## Kết quả kiểm tra

| Lệnh/kiểm tra thực tế | Kết quả |
|---|---|
| Backend `./mvnw clean verify` (Windows: `.\\mvnw.cmd clean verify`) | PASS: 342 test, 0 failure/error, 0 skipped; build thành công. |
| Sau đó bổ sung hai test traversal và chạy `.\\mvnw.cmd clean -Dtest=StoredFileSecurityTests verify` | PASS: 28 test upload, gồm 26 test đã chạy trong lượt đầy đủ và 2 test đường dẫn mới. Không cộng hai con số như hai tập độc lập. |
| Frontend `npm ci` | PASS: cài dependency theo lockfile; npm audit báo 0 vulnerabilities. |
| `npm run typecheck` | PASS. |
| `npm run lint` | PASS. |
| `npm test -- --maxWorkers=2` | PASS: 273 test / 20 file. Giới hạn worker để kiểm soát tài nguyên, không lọc hay bỏ test. |
| `npm run test:utils` | PASS: 24 test. |
| `npm run build` | PASS. |
| `docker compose config` | PASS; không in cấu hình đã resolve vì có thể chứa thông tin môi trường nhạy cảm. |
| `docker compose build backend frontend` | PASS. |
| Docker local sau khi cập nhật backend/frontend | PASS: backend health HTTP 200, frontend HTTP 200, MySQL healthy. Chỉ recreate backend/frontend; MySQL/volume và migration service không được recreate/chạy lại. |
| `git diff --check` | PASS. Không commit/push/merge. |

Một lượt frontend chạy song song nhiều build đã có test luồng email vượt timeout 5 giây. Giữ nguyên assertions và timeout, chạy lại **toàn bộ** bộ test được 273/273 PASS. Không vô hiệu hóa test để làm kết quả xanh.

Log kiểm chứng nằm trong thư mục tạm của máy chạy: `qldetai-audit-backend-final.log`, `qldetai-audit-upload-paths.log`, `qldetai-audit-front-final.log`, `qldetai-audit-npm-ci.log`, `qldetai-audit-docker-build.log`.

## Giới hạn và việc còn cần xác nhận

- Không chạy test phá dữ liệu trên MySQL đang phục vụ ứng dụng. Integration/concurrency trong lượt này dùng H2 cô lập; chưa thay cho kiểm thử contention/trigger trên MySQL 8.4 cô lập và kiểm thử tải thực tế.
- Chưa tự động dọn lịch sử file mồ côi vì chưa có chính sách retention/khôi phục được xác nhận. Đã giới hạn tăng trưởng, giảm upload lặp và giữ cleanup rollback; không xóa dữ liệu cũ để làm sạch báo cáo.
- Chưa chạy nghiệm thu trực quan đầy đủ trên mọi kích thước màn hình/tài khoản thật. Có test component, route và workflow; không tuyên bố đã kiểm tra thủ công tất cả giao diện responsive.
- Chưa có antivirus/content-disarm cho tài liệu Office/ZIP. Kiểm tra hiện có là allowlist/chữ ký, không phải cam kết tệp không chứa mã độc.
- Chưa chạy GitHub Actions từ remote vì không commit/push. Kết quả bên dưới là build/test thực tế tại workspace.
