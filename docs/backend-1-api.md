# Backend 1 — hợp đồng API và hướng dẫn tích hợp

## Phạm vi đã triển khai

- Đăng nhập/đăng xuất bằng Spring Security session; không phát JWT.
- CSRF cookie cho mọi yêu cầu thay đổi dữ liệu.
- Tài khoản `STUDENT`, `LECTURER`, `ADMIN`; hồ sơ sinh viên/giảng viên.
- Nhiệm vụ học thuật `DEAN`, `DEPARTMENT_HEAD` có thời gian hiệu lực và phạm vi khoa/bộ môn.
- Danh bạ giảng viên để frontend chọn người hướng dẫn, phản biện hoặc thành viên hội đồng.
- Danh mục khoa, bộ môn, học kỳ và đợt đăng ký.
- Thông báo cá nhân, trạng thái đã đọc và chống gửi trùng bằng `event_key`.
- Audit AOP ghi người thao tác, request ID, kết quả và thời gian xử lý.
- Lỗi và phân trang dùng một định dạng chung.

Base URL mặc định: `/api/v1`.

## Đăng nhập session và CSRF

Frontend phải gửi cookie bằng `withCredentials: true` và không lưu access token.

1. Gọi `GET /api/v1/auth/csrf` để nhận cookie `XSRF-TOKEN` và token trong response.
2. Gửi token qua header `X-XSRF-TOKEN` cho `POST`, `PUT`, `PATCH`, `DELETE`.
3. Gọi `POST /api/v1/auth/login` với JSON:

   ```json
   {
     "username": "admin@demo.local",
     "password": "configured-demo-password"
   }
   ```

   `username` chấp nhận email, MSSV hoặc mã giảng viên.

4. Trình duyệt giữ cookie `JSESSIONID`; kiểm tra phiên bằng `GET /api/v1/auth/me`.
5. Đăng xuất bằng `POST /api/v1/auth/logout` kèm CSRF token.

Response đăng nhập không có `accessToken`:

```json
{
  "success": true,
  "message": "Login successful.",
  "data": {
    "user": {
      "id": 1,
      "fullName": "Quản trị viên demo",
      "username": "admin@demo.local",
      "roles": ["ADMIN"],
      "student": null,
      "lecturer": null
    }
  },
  "timestamp": "2026-09-14T08:00:00Z"
}
```

## Endpoint

### Xác thực

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/auth/csrf` | Công khai | Khởi tạo CSRF cookie |
| POST | `/auth/login` | Công khai + CSRF | Đăng nhập và tạo session |
| GET | `/auth/me` | Đã đăng nhập | Hồ sơ, role và `activeAppointments` hiện tại |
| POST | `/auth/change-password` | Đã đăng nhập + CSRF | Đổi mật khẩu |
| POST | `/auth/logout` | Đã đăng nhập + CSRF | Hủy session |

`GET /auth/me` trả thêm `activeAppointments` cho giảng viên đang có nhiệm vụ học thuật còn hiệu lực:

```json
{
  "success": true,
  "data": {
    "id": 12,
      "fullName": "TS. Nguyễn Minh Đức",
      "username": "dean@demo.local",
    "roles": ["LECTURER"],
    "lecturer": {
      "id": 4,
        "lecturerCode": "GV001",
        "academicDegree": "TS.",
        "specialization": "Khoa học dữ liệu"
    },
    "activeAppointments": [
      {
        "id": 9,
        "lecturerId": 1,
        "lecturerCode": "GV001",
        "lecturerName": "TS. Nguyễn Minh Đức",
        "facultyId": 1,
        "departmentId": null,
        "positionCode": "DEAN",
        "startsAt": "2026-09-01T08:00:00",
        "endsAt": null
      }
    ]
  },
  "timestamp": "2026-09-18T08:00:00Z"
}
```

### Tổng quan

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/dashboard` | Đã đăng nhập | Số liệu đề tài, đợt đăng ký, thông báo, lịch gần nhất và dữ liệu biểu đồ tổng quan |

### Tài khoản và nhiệm vụ

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET/POST | `/admin/users` | `ADMIN` | Danh sách/tạo tài khoản |
| GET | `/admin/users/{id}` | `ADMIN` | Chi tiết tài khoản |
| DELETE | `/admin/users/{id}` | `ADMIN` | Vô hiệu hóa tài khoản, giữ dữ liệu lịch sử |
| PATCH | `/admin/users/{id}/status` | `ADMIN` | `ACTIVE`, `LOCKED`, `DISABLED` |
| PUT | `/admin/users/{id}/roles` | `ADMIN` | Thay danh sách role |
| GET | `/lecturers` | Đã đăng nhập | Danh bạ giảng viên, hỗ trợ `search`, `facultyId`, `departmentId`, `page`, `size` |
| GET/POST | `/admin/academic-appointments` | `ADMIN` | Tra cứu/cấp nhiệm vụ học thuật |
| PATCH | `/admin/academic-appointments/{id}/end` | `ADMIN` | Kết thúc nhiệm vụ |

Khi tạo tài khoản có role `STUDENT` hoặc `LECTURER`, request phải kèm đúng hồ sơ tương ứng. Mật khẩu được băm BCrypt; API không trả `password_hash`.
Danh sách tài khoản trả thêm `createdAt`, `lastLoginAt` và `lastLoginIp`; tài khoản bị xóa được vô hiệu hóa để giữ toàn vẹn dữ liệu học vụ.

`GET /lecturers` chỉ trả về giảng viên có tài khoản `ACTIVE`. Response mỗi item gồm:

- `id`, `userId`
- `lecturerCode`, `fullName`
- `academicDegree`, `specialization`
- `facultyId`, `facultyName`
- `departmentId`, `departmentName`

### Danh mục và đợt

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/faculties` | Đã đăng nhập | Danh sách khoa |
| POST/PATCH | `/faculties`, `/faculties/{id}` | `ADMIN` | Tạo/cập nhật khoa |
| GET | `/departments?facultyId=` | Đã đăng nhập | Danh sách bộ môn |
| POST | `/departments` | `ADMIN` hoặc trưởng khoa đúng phạm vi | Tạo bộ môn |
| PATCH | `/departments/{id}` | `ADMIN`, trưởng khoa hoặc trưởng bộ môn đúng phạm vi | Cập nhật bộ môn |
| GET/POST | `/semesters` | GET: đã đăng nhập; POST: `ADMIN` | Học kỳ |
| GET/POST | `/registration-periods` | GET: đã đăng nhập; POST: `ADMIN` hoặc trưởng khoa đúng phạm vi | Danh sách/tạo đợt nháp |
| GET/PUT | `/registration-periods/{id}` | GET: đã đăng nhập; PUT: người quản lý đúng phạm vi | Xem/sửa đợt nháp |
| PATCH | `/registration-periods/{id}/status` | Người quản lý đúng phạm vi | Chuyển trạng thái đợt |

Thứ tự thời gian bắt buộc:

`proposalStartAt < proposalEndAt <= registrationStartAt < registrationEndAt`.

- `SPECIALIZED`, `GRADUATION`: phải có `reviewDeadlineAt` trước khi chuyển `ACTIVE`.
- Chỉ `GRADUATION` có `defenseDate`; trường này bắt buộc trước khi chuyển `ACTIVE`.
- Chuyển trạng thái: `DRAFT -> ACTIVE/CANCELLED`, `ACTIVE -> CLOSED/CANCELLED`, `CLOSED -> ACTIVE`; `CANCELLED` là trạng thái kết thúc.
- Request cập nhật/chuyển trạng thái phải gửi `version`; nếu dữ liệu đã bị người khác sửa, API trả `409`.

### Luồng sinh viên

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/groups/mine` | `STUDENT` | Nhóm hiện tại của sinh viên trong các đợt đăng ký |
| GET | `/group-invitations/mine` | `STUDENT` | Lời mời tham gia nhóm của sinh viên |
| GET | `/topic-registrations/mine` | `STUDENT` | Hồ sơ đăng ký đề tài của các nhóm mà sinh viên còn là thành viên |
| GET | `/project-assignments/mine` | `STUDENT` | Đề tài đã được giao cho các nhóm mà sinh viên còn là thành viên |
| GET | `/project-assignments/{id}/milestones` | Thành viên nhóm hoặc người quản lý đề tài | Mốc báo cáo của đề tài được phép xem |
| GET | `/files/{id}/download` | Người có quyền xem tệp | Tải tệp báo cáo hoặc tệp đính kèm thông báo |
| GET | `/student/defense-sessions` | `STUDENT` | Lịch bảo vệ của nhóm sinh viên |
| GET | `/student/results` | `STUDENT` | Chỉ các kết quả `PUBLISHED` có bản chụp điểm/tiêu chí của chính sinh viên |

Endpoint quản trị `/topic-registrations`, `/project-assignments`, `/defense-sessions` và `/results` không dùng cho
giao diện sinh viên. Điều này tránh trả về dữ liệu của nhóm khác và tránh các lỗi phân quyền
khi tải tiến độ, lịch bảo vệ hoặc kết quả.

### Thông báo và audit

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/notifications` | Đã đăng nhập | Thông báo của chính người dùng |
| GET | `/notifications/unread-count` | Đã đăng nhập | Số chưa đọc |
| PATCH | `/notifications/{id}/read` | Chủ thông báo | Đánh dấu đã đọc |
| GET | `/admin/audit-logs?entityType=` | `ADMIN` | Tra cứu audit |

Backend 2/3 tích hợp thông báo bằng `NotificationService.publish(...)` và audit bằng annotation:

```java
@BusinessAudit(
    action = "TOPIC_APPROVE",
    entityType = "TOPIC",
    entityIdArgument = 0
)
```

Không đưa mật khẩu, token, nội dung tệp hoặc dữ liệu nhạy cảm vào audit.

## Định dạng lỗi

```json
{
  "success": false,
  "message": "You do not have permission to perform this action.",
  "timestamp": "2026-09-14T08:00:00Z"
}
```

- `400`: payload/validation/quy tắc nghiệp vụ không hợp lệ.
- `401`: chưa đăng nhập hoặc thông tin đăng nhập sai.
- `403`: sai role, nhiệm vụ hoặc phạm vi.
- `404`: không tìm thấy tài nguyên.
- `409`: trùng dữ liệu hoặc optimistic-lock conflict.

Mỗi response có header `X-Request-Id`; audit dùng cùng mã để đối chiếu.

## Dữ liệu demo

Docker nạp `database/seed/demo-data.sql` khi MySQL khởi tạo volume mới. Bộ dữ
liệu gồm khoa CNTT, ba bộ môn, các đợt `COURSE`, `SPECIALIZED`, `GRADUATION`,
đề tài, nhóm, phân công, hội đồng và một kết quả đã công bố. Xem tài khoản tại
[`demo-accounts.md`](demo-accounts.md).

## Kiểm thử

```powershell
cd backend
mvn test
```

Test dùng H2 độc lập, không sửa database MySQL cục bộ.
