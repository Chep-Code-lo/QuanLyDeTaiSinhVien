# Backend — Quản lý đề tài sinh viên

Backend dùng Spring Boot, Java 21 và Maven Wrapper. Hướng dẫn cấu hình MySQL,
biến môi trường và Docker nằm trong [README của dự án](../README.md).

## Cấu trúc mã nguồn

Các package nằm dưới `src/main/java/com/example/qldetai`:

| Package | Trách nhiệm |
| --- | --- |
| `feature/academic` | Khoa, bộ môn, học kỳ, đợt đăng ký và quyền quản lý học vụ |
| `feature/identity` | Tài khoản, hồ sơ, vai trò và bổ nhiệm học vụ |
| `feature/topic` | Đề tài, giảng viên hướng dẫn và xét duyệt |
| `feature/groupregistration` | Nhóm, lời mời và đăng ký đề tài |
| `feature/project` | Phân công đề tài chính thức cho nhóm |
| `feature/progress` | Mốc tiến độ, nộp báo cáo, phản hồi và tệp |
| `feature/evaluation` | Tiêu chí, phân công chấm, hội đồng, bảo vệ, điểm và kết quả |
| `common/api` | Cấu trúc phản hồi và phân trang dùng chung |
| `config`, `security`, `exception`, `aspect` | Cấu hình, xác thực, xử lý lỗi và audit |
| `system` | Health check và metadata API |

Mỗi feature tổ chức theo `controller`, `service`, `repository`, `domain`, `dto`;
chỉ thêm `mapper` khi có nhu cầu. Controller xử lý HTTP và gọi service. Service
điều phối nghiệp vụ, truy vấn và transaction. Repository truy cập dữ liệu.

## Hồ sơ và chuyển đổi DTO

`IdentityMapper` chuyển dữ liệu được truyền vào thành DTO, không gọi repository.
`UserProfileService` tải dữ liệu hồ sơ và bổ nhiệm. Khi trả danh sách người dùng,
service lấy hồ sơ sinh viên và giảng viên theo tập ID của trang hiện tại, thay vì
truy vấn riêng hai loại hồ sơ cho từng người. Phân trang và thứ tự của trang
người dùng được giữ nguyên. Cơ chế này không có nghĩa mọi quan hệ lazy khác đã
được tối ưu truy vấn.

## Phân quyền tài nguyên

`AcademicAuthorization` chỉ kiểm tra quyền theo khoa, bộ môn và đợt. Các bean
`TopicAuthorization`, `RegistrationAuthorization`, `ProjectAuthorization`,
`ProgressAuthorization`, `EvaluationAuthorization` kiểm tra tài nguyên thuộc
feature của mình và gọi lớp phân quyền liên quan theo quan hệ sở hữu.

Các biểu thức `@PreAuthorize` dùng các bean này. ID khoa/bộ môn phải được suy ra
từ tài nguyên trong database. Không thay bằng ID do client tự khai báo. Quyền
ADMIN và quyền nghiệp vụ hiện có được giữ nguyên trong quá trình tách lớp.

## Module đánh giá

Controller được nhóm theo tài nguyên: thành phần đánh giá, rubric, phân công
chấm, phiếu điểm, hội đồng, lịch bảo vệ, kết quả và yêu cầu sửa điểm. Các URL,
HTTP method và cấu trúc phản hồi API được giữ nguyên.

- `EvaluationService`: cấu hình đánh giá, phân công và phiếu điểm.
- `EvaluationCommitteeService`: hội đồng và thành viên.
- `DefenseSessionService`: lịch bảo vệ và điều kiện phản biện trước bảo vệ.
- `EvaluationResultService`: tổng hợp, lưu snapshot và công bố kết quả.
- `GradeChangeService`: yêu cầu và xét duyệt sửa điểm.
- `EvaluationActorAccess`: kiểm tra vai trò người thao tác và tải tài khoản
  dùng chung trong các service đánh giá; không thay thế phân quyền tài nguyên.

Các phương thức nghiệp vụ giữ `@Transactional` và `@BusinessAudit` tại service
thực hiện thay đổi. Không bỏ các bước flush phục vụ trigger MySQL và snapshot.

## Kiểm tra

Chạy từ thư mục `backend` trên Windows:

```powershell
.\mvnw.cmd clean test
```

Test theo feature nằm trong `src/test/java/com/example/qldetai/feature`.
`workflow/ProjectLifecycleIntegrationTests` kiểm tra luồng xuyên nhiều feature,
bao gồm đăng ký, phân công, chấm điểm và kết quả. Các test sử dụng H2 theo cấu
hình trong `src/test/resources/application.properties`; không thay thế kiểm tra
trigger và hành vi riêng của MySQL trên database thực.

`target/` là kết quả build có thể tạo lại. `.mvn/`, `mvnw`, `mvnw.cmd` là công cụ
build cần giữ. `HELP.md`, `.github/modernize/` và `.vscode/` là tài liệu/cấu hình
công cụ cục bộ, không thuộc kiến trúc nghiệp vụ.
