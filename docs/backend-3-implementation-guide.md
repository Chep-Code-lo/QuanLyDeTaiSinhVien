# Backend 3 — hướng dẫn đọc code và ghép API

Tài liệu này dành cho người phụ trách Backend 3, giải thích các mục bạn trong nhóm yêu cầu: tệp báo cáo, danh mục và API đọc đánh giá, gán người chấm theo lịch bảo vệ, mở lại phiếu điểm. Backend sử dụng Spring MVC REST API; frontend gọi các endpoint bắt đầu bằng `/api/v1`.

## Constructor injection là gì?

Ví dụ lấy ngay từ `EvaluationCatalogController`:

```java
private final EvaluationService evaluationService;

public EvaluationCatalogController(EvaluationService evaluationService) {
    this.evaluationService = evaluationService;
}
```

`EvaluationService` là đối tượng controller cần dùng. Spring tạo đối tượng đó và truyền vào constructor. `final` nghĩa là sau khi nhận, controller không gán dependency sang đối tượng khác. Class chỉ có một constructor nên không cần viết `@Autowired`.

Khi đọc code, theo thứ tự: controller nhận request → service kiểm tra quyền và quy tắc → repository đọc/ghi database → DTO chứa dữ liệu trả về. Entity mô tả bảng; DTO mô tả dữ liệu của API. Không trả trực tiếp entity ra frontend vì có thể lộ thông tin nội bộ hoặc gặp quan hệ dữ liệu vòng nhau.

## File cần đọc theo từng công việc

| Công việc | File bắt đầu đọc | Điều cần hiểu |
|---|---|---|
| Upload và đọc file | `StoredFileController`, `StoredFileService` | Upload dùng multipart, lấy `data.id` làm `fileId` khi nộp báo cáo |
| Thành phần và rubric | `EvaluationCatalogController`, `EvaluationService` | Tạo thành phần → tạo rubric → khóa rubric → khóa thành phần |
| Danh sách phục vụ frontend | `EvaluationReadController`, `EvaluationAdministrationService` | Bộ lọc, phân trang, kiểm tra quyền xem kết quả |
| Gán giảng viên chấm | `EvaluationDtos.CreateEvaluationRequest`, `EvaluationService.assign` | Phân biệt `REVIEW` và `COMMITTEE` bằng loại của thành phần trong database |
| Lưu và nộp điểm | `EvaluationService.saveSheet` | Chỉ người được giao chấm được ghi phiếu, kiểm tra điểm và `version` |
| Duyệt đổi điểm | `EvaluationAdministrationService.reviewChange` | Duyệt chỉ mở lại phiếu; phải lưu điểm thành công mới áp dụng yêu cầu |

## Trình tự thử bằng Postman

Chuẩn bị một `projectAssignment` đã được giao từ Backend 2. Các ID trong ví dụ phải thay bằng ID thật nhận từ response. Các thời điểm `deadlineAt`, `dueAt` phải nằm trong tương lai và viết theo dạng ISO local datetime, ví dụ `2026-12-20T10:00:00`.

1. Gọi `GET /api/v1/auth/csrf`, giữ cookie và token; đăng nhập bằng `POST /api/v1/auth/login`. Sau đăng nhập, lấy lại CSRF token và gửi header `X-XSRF-TOKEN` cho các request ghi dữ liệu. Giữ cookie session trong Postman.
2. Tạo mốc bằng `POST /api/v1/milestones` với tài khoản được phép. Đăng nhập tài khoản nhóm trưởng, gọi `POST /api/v1/files`, trong Body chọn **form-data**, thêm key `file`, chọn kiểu **File**, chọn báo cáo. Không tự gõ header `Content-Type`: Postman tự thêm multipart boundary.
3. Lấy `data.id` của file để gọi `POST /api/v1/milestones/{id}/submissions` với `{ "fileId": 501, "note": "Báo cáo tuần 3" }`.
4. Với tài khoản quản lý, gọi `POST /api/v1/assessment-components`, tạo rubric qua `POST /api/v1/rubrics` với tổng điểm tối đa các tiêu chí bằng 10. Gọi `PATCH /api/v1/rubrics/{id}/lock` rồi `PATCH /api/v1/assessment-components/{id}/lock`.
5. Gán giảng viên bằng `POST /api/v1/evaluation-assignments`. Nếu loại là `COMMITTEE`, tạo hội đồng, thêm 3–5 thành viên và tạo lịch bảo vệ trước.
6. Đăng nhập tài khoản giảng viên được giao; đọc `GET /api/v1/evaluation-assignments/{id}/score-sheet`, lấy `version`, rồi lưu/nộp qua `PUT` cùng đường dẫn. Sau mỗi lần ghi, dùng `version` mới trong response cho lần tiếp theo.

Các request JSON đầy đủ nằm trong [hợp đồng Backend 3](./backend-3-api.md).

## Tại sao cần defenseId?

`assignmentId` là quyết định giao đề tài cho nhóm; `defenseId` là một buổi bảo vệ. Hai ID có ý nghĩa khác nhau.

| Loại thành phần | defenseId trong request | Backend kiểm tra |
|---|---|---|
| `REVIEW` | `null` | Người chấm phản biện không gắn với lịch bảo vệ |
| `COMMITTEE` | Bắt buộc có ID | Đúng đề tài, đúng đợt và giảng viên thuộc hội đồng của lịch đó |
| `OTHER` | `null` | Không gắn lịch bảo vệ |

Ví dụ phần khác nhau của payload:

```json
{
  "assignmentId": 300,
  "rubricId": 35,
  "lecturerId": 18,
  "defenseId": 77,
  "deadlineAt": "2026-12-20T10:00:00"
}
```

Frontend chọn loại theo `componentType` của rubric/thành phần đã tải; backend đọc lại giá trị trong database để kiểm tra. Giấu ô chọn lịch trên giao diện chưa đủ để bảo vệ quy tắc này.

## Hiểu luồng đổi điểm

| Hành động | Trạng thái yêu cầu | Trạng thái phiếu |
|---|---|---|
| Giảng viên gửi yêu cầu | `PENDING` | `SUBMITTED` hoặc `LOCKED` |
| Quản lý chấp thuận | `APPROVED` | `DRAFT` |
| Giảng viên lưu nháp thành công lần tiếp theo | `APPLIED`, có `appliedAt` | `DRAFT` |
| Hoặc giảng viên nộp thành công lần tiếp theo | `APPLIED`, có `appliedAt` | `SUBMITTED` |
| Lưu bị từ chối do điểm sai hoặc version cũ | Vẫn `APPROVED` | Giữ nguyên |

`APPROVED` nghĩa là được phép sửa. `APPLIED` nghĩa là giảng viên đã thực sự lưu phiếu thành công. Không đặt `APPLIED` ngay lúc duyệt vì lúc đó chưa có điểm được sửa.

`@Transactional` giúp thao tác lưu điểm và đổi trạng thái yêu cầu cùng thành công hoặc cùng rollback. `flush()` gửi các thay đổi đang chờ xuống database, chưa phải commit. Ở đây thứ tự rất quan trọng: lưu `APPROVED` trước khi mở khóa phiếu; ghi điểm chi tiết khi phiếu còn `DRAFT`; cuối cùng mới chuyển sang `SUBMITTED`. Các trigger MySQL kiểm tra những điều kiện này khi từng câu SQL chạy.

## Các API đọc cho frontend

| Endpoint | Bộ lọc có sẵn |
|---|---|
| `GET /assessment-components` | `periodId` |
| `GET /rubrics` | `componentId` |
| `GET /evaluation-assignments` | `periodId`, `assignmentId`, `lecturerId`, `status`, `page`, `size` |
| `GET /evaluation-committees` | `periodId`, `page`, `size` |
| `GET /defense-sessions` | `periodId`, `committeeId`, `page`, `size` |
| `GET /results` | `periodId`, `assignmentId`, `status`, `page`, `size` |
| `GET /results/{id}` | ID trên đường dẫn |
| `GET /grade-change-requests` | `status`, `sheetId`, `page`, `size` |

Hai danh mục đầu trả mảng trong `data`; các danh sách phân trang trả `data.content` cùng `totalElements`, `totalPages`. Trang bắt đầu từ 0, `size` từ 1 đến 100. Sinh viên chỉ nhận kết quả `PUBLISHED` của nhóm mình, kể cả khi tự truyền ID nhóm khác trong URL.

## Chạy kiểm thử trong terminal VS Code

```powershell
cd D:\LTWDT\QuanLyDeTaiSinhVien\backend
mvn test
```

Test dùng H2, không yêu cầu MySQL đang chạy và không ghi vào database thật của nhóm. Trước khi demo với MySQL, cần import schema đã thống nhất và cấu hình `DB_USERNAME`, `DB_PASSWORD`. Không dùng kết quả build/test H2 để kết luận các trigger MySQL đã được chạy thử.
