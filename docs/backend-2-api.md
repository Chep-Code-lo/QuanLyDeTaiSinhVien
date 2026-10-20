# Backend 2 — hợp đồng API và payload mẫu

Tài liệu này mô tả các API của phần `backend2` theo đúng code hiện tại. Mục tiêu là để frontend, người test API, hoặc người thuyết trình có thể nhìn nhanh:

- Endpoint nào đang có.
- Endpoint đó cần quyền gì.
- Payload request mẫu trông như thế nào.
- Response thực tế được trả về theo format nào.

Base URL mặc định: `/api/v1`.

## Phạm vi backend2

Backend 2 hiện xử lý 3 mảng:

- Quản lý đề tài.
- Quản lý nhóm sinh viên và đăng ký đề tài.
- Giao đề tài chính thức cho nhóm.

Các API về tiến độ, báo cáo, hội đồng, lịch, đánh giá và kết quả được tách riêng tại [docs/backend-3-api.md](./backend-3-api.md).

## Phụ thuộc xác thực

Backend 2 dùng chung cơ chế session và CSRF của backend1.

Frontend hoặc Postman cần:

1. Gọi `GET /api/v1/auth/csrf`.
2. Lưu cookie `XSRF-TOKEN` và `JSESSIONID`.
3. Gửi header `X-XSRF-TOKEN` cho `POST`, `PUT`, `PATCH`, `DELETE`.
4. Gửi cookie kèm mọi request có xác thực.

Chi tiết đăng nhập xem thêm tại [docs/backend-1-api.md](./backend-1-api.md).

## Format response chung

Response thành công:

```json
{
  "success": true,
  "message": "Request completed successfully.",
  "data": {},
  "timestamp": "2026-09-15T16:00:00Z"
}
```

Response phân trang:

```json
{
  "success": true,
  "message": "Request completed successfully.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  },
  "timestamp": "2026-09-15T16:00:00Z"
}
```

Response lỗi:

```json
{
  "success": false,
  "message": "Only approved topics can be published.",
  "timestamp": "2026-09-15T16:00:00Z"
}
```

## Enum thường gặp

### Đề tài

- `ProposalSource`: `STUDENT`, `LECTURER`
- `VisibilityType`: `OPEN`, `RESERVED`
- `TopicStatus`: `DRAFT`, `PENDING`, `REVISION_REQUIRED`, `APPROVED`, `PUBLISHED`, `REJECTED`, `WITHDRAWN`
- `TopicReviewDecision`: `APPROVED`, `REVISION_REQUIRED`, `REJECTED`

### Nhóm và đăng ký

- `GroupStatus`: `FORMING`, `ACTIVE`, `LOCKED`, `CLOSED`
- `GroupMemberRole`: `LEADER`, `MEMBER`
- `RegistrationStatus`: `PENDING`, `APPROVED`, `REJECTED`, `WITHDRAWN`

### Assignment

- `AssignmentStatus`: `IN_PROGRESS`, `COMPLETED`, `CANCELLED`

## 1. API đề tài

### Danh sách endpoint

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/topics` | Đã đăng nhập | Danh sách đề tài |
| GET | `/topics/{id}` | Đã đăng nhập | Chi tiết đề tài |
| POST | `/topics` | `ADMIN`, `LECTURER`, `STUDENT` | Tạo đề tài |
| POST | `/topics/for-registration` | `ADMIN` hoặc giảng viên đang giữ chức Trưởng khoa/Trưởng bộ môn đúng phạm vi | Tạo, phê duyệt, gán hướng dẫn và công bố đề tài trong một giao dịch |
| PUT | `/topics/{id}` | `ADMIN`, `LECTURER`, `STUDENT` | Sửa nội dung đề tài |
| PUT | `/topics/{id}/supervisors` | `ADMIN`, `LECTURER` | Gán giảng viên hướng dẫn |
| PATCH | `/topics/{id}/review` | `ADMIN`, `LECTURER` | Review đề tài |
| PATCH | `/topics/{id}/publish` | `ADMIN`, `LECTURER` | Publish đề tài |
| PATCH | `/topics/{id}/withdraw` | `ADMIN`, `LECTURER`, `STUDENT` | Rút đề tài |

### GET `/topics`

Query params:

- `periodId` tùy chọn
- `status` tùy chọn
- `page` mặc định `0`
- `size` mặc định `20`, tối đa `100`

Ví dụ:

```http
GET /api/v1/topics?periodId=1&status=PUBLISHED&page=0&size=20
```

### POST `/topics`

Request mẫu:

```json
{
  "periodId": 1,
  "code": "DT-API-01",
  "title": "Xây dựng API quản lý đề tài",
  "description": "Thiết kế backend cho hệ thống quản lý đề tài sinh viên.",
  "objectives": "Xây dựng đủ các API nghiệp vụ chính.",
  "expectedOutcomes": "Có thể tích hợp với frontend và test bằng Postman.",
  "prerequisites": "Biết Spring Boot và JPA.",
  "visibilityType": "OPEN",
  "reservedGroupId": null
}
```

Trường hợp đề tài dành riêng cho một nhóm:

```json
{
  "periodId": 1,
  "code": "DT-RS-01",
  "title": "Đề tài dành riêng cho nhóm đã được chọn",
  "description": "Đề tài chỉ cho một nhóm đăng ký.",
  "objectives": "Thử nghiệm flow reserve topic.",
  "expectedOutcomes": "Nhóm đúng mới đăng ký được.",
  "prerequisites": null,
  "visibilityType": "RESERVED",
  "reservedGroupId": 15
}
```

Response mẫu:

```json
{
  "success": true,
  "message": "Topic proposal created.",
  "data": {
    "id": 10,
    "periodId": 1,
    "periodName": "Đợt đồ án kỳ 1",
    "facultyId": 1,
    "facultyName": "Công nghệ thông tin",
    "departmentId": 3,
    "departmentName": "Công nghệ phần mềm",
    "code": "DT-API-01",
    "title": "Xây dựng API quản lý đề tài",
    "description": "Thiết kế backend cho hệ thống quản lý đề tài sinh viên.",
    "objectives": "Xây dựng đủ các API nghiệp vụ chính.",
    "expectedOutcomes": "Có thể tích hợp với frontend và test bằng Postman.",
    "prerequisites": "Biết Spring Boot và JPA.",
    "proposedBy": 8,
    "proposerName": "Nguyễn Văn A",
    "proposalSource": "LECTURER",
    "visibilityType": "OPEN",
    "reservedGroupId": null,
    "reservedGroupName": null,
    "status": "PENDING",
    "publishedAt": null,
    "createdAt": "2026-09-15T16:00:00",
    "updatedAt": "2026-09-15T16:00:00",
    "version": 0,
    "supervisors": [],
    "latestReview": null
  },
  "timestamp": "2026-09-15T16:00:00Z"
}
```

### POST `/topics/for-registration`

Được sử dụng tại trang `/registrations` cho chức năng **Tạo và công bố**.
Trưởng khoa được tạo đề tài cho các bộ môn thuộc khoa đang phụ trách;
Trưởng bộ môn chỉ được tạo cho bộ môn đang phụ trách. Bổ nhiệm đã kết thúc
hoặc chưa có hiệu lực không cấp quyền, kể cả với phiên đăng nhập cũ.
Quản trị viên được sử dụng chức năng mà không cần hồ sơ giảng viên.

```json
{
  "periodId": 1,
  "departmentId": 1,
  "code": "KLTN-2026-01",
  "title": "Hệ thống hỗ trợ sinh viên",
  "description": "Mô tả nội dung đề tài",
  "objectives": "Mục tiêu thực hiện",
  "expectedOutcomes": "Ứng dụng và báo cáo hoàn chỉnh",
  "prerequisites": "Kiến thức lập trình web",
  "primaryLecturerId": 1,
  "secondaryLecturerId": null
}
```

Đợt phải `ACTIVE`, bộ môn phải thuộc khoa của đợt và còn hoạt động.
Cho phép tạo từ lúc bắt đầu đề xuất đến hết thời gian đăng ký, bao gồm cả
giai đoạn đăng ký sau khi đóng đề xuất. Giảng viên hướng dẫn phải đang hoạt
động, thuộc cùng khoa và hai người hướng dẫn phải khác nhau nếu chọn cả hai.

Trả `201` với `TopicResponse`: trạng thái `PUBLISHED`, phạm vi `OPEN`,
có hướng dẫn chính và lịch sử phê duyệt `APPROVED`. Nếu thông tin không hợp
lệ thì không lưu đề tài, quyết định duyệt hoặc phân công hướng dẫn dang dở.
Sinh viên đăng ký qua `POST /topic-registrations` theo các quy tắc hiện hành
của đợt và nhóm.

### PUT `/topics/{id}`

Request mẫu:

```json
{
  "version": 0,
  "title": "Xây dựng API quản lý đề tài sinh viên",
  "description": "Cập nhật mô tả đề tài.",
  "objectives": "Bổ sung đầy đủ API và rule nghiệp vụ.",
  "expectedOutcomes": "Có thể triển khai demo hoàn chỉnh.",
  "prerequisites": "Spring Boot, JPA, Security.",
  "visibilityType": "OPEN",
  "reservedGroupId": null
}
```

Lưu ý:

- Chỉ proposer hoặc admin mới sửa được.
- Chỉ các trạng thái `DRAFT`, `PENDING`, `REVISION_REQUIRED` mới sửa được.
- Bắt buộc gửi `version`.

### PUT `/topics/{id}/supervisors`

Request mẫu:

```json
{
  "version": 1,
  "primaryLecturerId": 12,
  "secondaryLecturerId": 14
}
```

Rule:

- Chỉ gán được khi topic đã `APPROVED` hoặc `PUBLISHED`.
- `primaryLecturerId` và `secondaryLecturerId` không được trùng nhau.

### PATCH `/topics/{id}/review`

Request mẫu:

```json
{
  "version": 0,
  "decision": "APPROVED",
  "comment": "Đề tài đạt yêu cầu và có thể công bố."
}
```

Hoặc yêu cầu sửa:

```json
{
  "version": 0,
  "decision": "REVISION_REQUIRED",
  "comment": "Cần bổ sung rõ mục tiêu và đầu ra."
}
```

### PATCH `/topics/{id}/publish`

Request mẫu:

```json
{
  "version": 2
}
```

Rule:

- Chỉ topic `APPROVED` mới publish được.
- Phải có supervisor chính slot `1`.

### PATCH `/topics/{id}/withdraw`

Request mẫu:

```json
{
  "version": 1,
  "reason": "Đề tài cần tạm dừng để điều chỉnh lại phạm vi."
}
```

Rule:

- Chỉ proposer hoặc admin được rút.
- Topic đã `PUBLISHED` thì không rút được.

## 2. API nhóm sinh viên

### Danh sách endpoint

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/groups` | Đã đăng nhập | Danh sách nhóm |
| GET | `/groups/mine` | `STUDENT` | Danh sách nhóm của tôi |
| GET | `/groups/{id}` | Đã đăng nhập | Chi tiết nhóm |
| POST | `/groups` | `STUDENT` | Tạo nhóm |
| POST | `/groups/{id}/members` | `STUDENT` | Thêm thành viên |
| DELETE | `/groups/{id}/members/{studentId}` | `STUDENT` | Xóa thành viên |

### POST `/groups`

Request mẫu:

```json
{
  "periodId": 1,
  "name": "Nhóm backend 2"
}
```

Response mẫu:

```json
{
  "success": true,
  "message": "Student group created.",
  "data": {
    "id": 21,
    "periodId": 1,
    "periodName": "Đợt đồ án kỳ 1",
    "name": "Nhóm backend 2",
    "status": "FORMING",
    "createdBy": 31,
    "creatorName": "Trần Thị B",
    "createdAt": "2026-09-15T16:10:00",
    "version": 0,
    "members": [
      {
        "studentId": 31,
        "userId": 44,
        "studentCode": "SV220001",
        "fullName": "Trần Thị B",
        "className": "D21CQCN01",
        "cohort": "K21",
        "slotNo": 1,
        "memberRole": "LEADER",
        "joinedAt": "2026-09-15T16:10:00"
      }
    ]
  },
  "timestamp": "2026-09-15T16:10:00Z"
}
```

Rule:

- Sinh viên chỉ được thuộc một nhóm active trong cùng period.
- Phải cùng khoa với period.

### POST `/groups/{id}/members`

Request mẫu:

```json
{
  "studentCode": "SV220002"
}
```

Rule:

- Chỉ leader được thêm thành viên.
- Nhóm tối đa 3 thành viên active.
- Không cho sửa nếu nhóm đã `LOCKED`, `CLOSED`, đã có assignment, hoặc đang có registration `PENDING/APPROVED`.

### DELETE `/groups/{id}/members/{studentId}`

Ví dụ:

```http
DELETE /api/v1/groups/21/members/32
```

Rule:

- Chỉ leader được xóa.
- Không được xóa leader.
- Thao tác này là soft delete theo `leftAt`, không xóa cứng lịch sử.

### GET `/groups/mine`

Response là danh sách `StudentGroupResponse`.

Use case:

- Trang “nhóm của tôi”.
- Kiểm tra sinh viên đang ở nhóm nào trước khi đăng ký đề tài.

## 3. API đăng ký đề tài

### Danh sách endpoint

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/topic-registrations` | Đã đăng nhập | Danh sách đăng ký đề tài |
| GET | `/topic-registrations/{id}` | Đã đăng nhập | Chi tiết đăng ký |
| POST | `/topic-registrations` | `STUDENT` | Tạo đăng ký đề tài |
| PATCH | `/topic-registrations/{id}/review` | `ADMIN`, `LECTURER` | Duyệt hoặc từ chối đăng ký |
| PATCH | `/topic-registrations/{id}/withdraw` | `STUDENT` | Rút đăng ký |

### POST `/topic-registrations`

Request mẫu:

```json
{
  "groupId": 21,
  "topicId": 10,
  "motivation": "Nhóm đã có kinh nghiệm phù hợp với đề tài này."
}
```

Response mẫu:

```json
{
  "success": true,
  "message": "Topic registration submitted.",
  "data": {
    "id": 100,
    "periodId": 1,
    "periodName": "Đợt đồ án kỳ 1",
    "groupId": 21,
    "groupName": "Nhóm backend 2",
    "topicId": 10,
    "topicCode": "DT-API-01",
    "topicTitle": "Xây dựng API quản lý đề tài",
    "registeredBy": 31,
    "registeredByName": "Trần Thị B",
    "motivation": "Nhóm đã có kinh nghiệm phù hợp với đề tài này.",
    "status": "PENDING",
    "registeredAt": "2026-09-15T16:20:00",
    "reviewedBy": null,
    "reviewedByName": null,
    "reviewedAt": null,
    "decisionReason": null,
    "version": 0
  },
  "timestamp": "2026-09-15T16:20:00Z"
}
```

Rule:

- Chỉ leader được đăng ký.
- Chỉ nhóm `ACTIVE` mới đăng ký được.
- Topic phải `PUBLISHED`.
- Topic và group phải cùng period.
- Nếu topic là `RESERVED` thì phải đúng nhóm được reserve.
- Mỗi nhóm chỉ có một registration `PENDING/APPROVED` tại một thời điểm.

### PATCH `/topic-registrations/{id}/review`

Duyệt:

```json
{
  "version": 0,
  "status": "APPROVED",
  "decisionReason": null
}
```

Từ chối:

```json
{
  "version": 0,
  "status": "REJECTED",
  "decisionReason": "Nhóm chưa đáp ứng tiêu chí của đề tài."
}
```

Rule:

- Chỉ `ADMIN` hoặc `LECTURER`.
- Chỉ registration `PENDING` mới được review.
- Nếu `REJECTED` thì bắt buộc có `decisionReason`.

### PATCH `/topic-registrations/{id}/withdraw`

Request mẫu:

```json
{
  "version": 0,
  "decisionReason": "Nhóm đổi sang đề tài khác."
}
```

Rule:

- Chỉ leader được rút.
- Chỉ registration `PENDING` mới rút được.

## 4. API giao đề tài

### Danh sách endpoint

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/project-assignments` | Đã đăng nhập | Danh sách assignment |
| GET | `/project-assignments/{id}` | Đã đăng nhập | Chi tiết assignment |
| POST | `/project-assignments` | `ADMIN`, `LECTURER` | Tạo quyết định giao đề tài |
| PATCH | `/project-assignments/{id}/status` | `ADMIN`, `LECTURER` | Cập nhật trạng thái assignment |

### POST `/project-assignments`

Request mẫu:

```json
{
  "registrationId": 100
}
```

Response mẫu:

```json
{
  "success": true,
  "message": "Project assignment created.",
  "data": {
    "id": 300,
    "registrationId": 100,
    "groupId": 21,
    "groupName": "Nhóm backend 2",
    "topicId": 10,
    "topicCode": "DT-API-01",
    "topicTitle": "Xây dựng API quản lý đề tài",
    "periodId": 1,
    "periodName": "Đợt đồ án kỳ 1",
    "assignedBy": 1,
    "assignedByName": "Quản trị demo",
    "assignedAt": "2026-09-15T16:30:00",
    "status": "IN_PROGRESS",
    "version": 0
  },
  "timestamp": "2026-09-15T16:30:00Z"
}
```

Rule:

- Chỉ registration `APPROVED` mới tạo assignment được.
- Không tạo trùng theo registration, group hoặc topic.
- Khi tạo assignment:
  - Group được chuyển sang `LOCKED`.
  - Các registration `PENDING` khác của cùng topic bị chuyển sang `REJECTED`.

### PATCH `/project-assignments/{id}/status`

Đánh dấu hoàn thành:

```json
{
  "version": 0,
  "status": "COMPLETED"
}
```

Hủy quyết định giao đề tài:

```json
{
  "version": 0,
  "status": "CANCELLED"
}
```

Rule chuyển trạng thái:

- `IN_PROGRESS -> COMPLETED`
- `IN_PROGRESS -> CANCELLED`
- `COMPLETED` không đổi tiếp
- `CANCELLED` không đổi tiếp

Tác động:

- `COMPLETED`: group chuyển sang `CLOSED`
- `CANCELLED`: group mở lại thành `ACTIVE` hoặc `FORMING` tùy số thành viên active

## 5. Danh sách field response chính

### `TopicResponse`

- `id`
- `periodId`, `periodName`
- `facultyId`, `facultyName`
- `departmentId`, `departmentName`
- `code`, `title`, `description`
- `objectives`, `expectedOutcomes`, `prerequisites`
- `proposedBy`, `proposerName`, `proposalSource`
- `visibilityType`, `reservedGroupId`, `reservedGroupName`
- `status`, `publishedAt`, `createdAt`, `updatedAt`
- `version`
- `supervisors`
- `latestReview`

### `StudentGroupResponse`

- `id`
- `periodId`, `periodName`
- `name`
- `status`
- `createdBy`, `creatorName`
- `createdAt`
- `version`
- `members`

### `TopicRegistrationResponse`

- `id`
- `periodId`, `periodName`
- `groupId`, `groupName`
- `topicId`, `topicCode`, `topicTitle`
- `registeredBy`, `registeredByName`
- `motivation`
- `status`
- `registeredAt`
- `reviewedBy`, `reviewedByName`, `reviewedAt`
- `decisionReason`
- `version`

### `ProjectAssignmentResponse`

- `id`
- `registrationId`
- `groupId`, `groupName`
- `topicId`, `topicCode`, `topicTitle`
- `periodId`, `periodName`
- `assignedBy`, `assignedByName`
- `assignedAt`
- `status`
- `version`

## 6. Lỗi thường gặp

- `400 Bad Request`
  - Sai validation.
  - Sai rule nghiệp vụ như publish khi chưa approved.
- `401 Unauthorized`
  - Chưa đăng nhập hoặc session hết hạn.
- `403 Forbidden`
  - Không đủ role.
  - Không phải leader nhưng lại thêm/xóa thành viên hoặc đăng ký topic.
- `404 Not Found`
  - Không tìm thấy topic, group, registration, lecturer, student.
- `409 Conflict`
  - Trùng dữ liệu.
  - `version` cũ hơn dữ liệu hiện tại.

Ví dụ conflict do optimistic locking:

```json
{
  "success": false,
  "message": "The topic was changed by another user. Reload and try again.",
  "timestamp": "2026-09-15T16:40:00Z"
}
```

## 7. Trình tự gọi API khuyến nghị

Flow chuẩn khi demo hoặc tích hợp:

1. Đăng nhập.
2. Tạo topic bằng `POST /topics`.
3. Review topic bằng `PATCH /topics/{id}/review`.
4. Gán supervisor bằng `PUT /topics/{id}/supervisors`.
5. Publish topic bằng `PATCH /topics/{id}/publish`.
6. Sinh viên tạo group bằng `POST /groups`.
7. Leader thêm member bằng `POST /groups/{id}/members`.
8. Group đăng ký topic bằng `POST /topic-registrations`.
9. Giảng viên/admin review registration bằng `PATCH /topic-registrations/{id}/review`.
10. Tạo assignment bằng `POST /project-assignments`.
11. Khi kết thúc, cập nhật `PATCH /project-assignments/{id}/status`.

## 8. Kiểm thử nhanh bằng Maven

```powershell
cd backend
mvn test
```

Tại thời điểm dựng tài liệu này, backend đã pass toàn bộ test hiện có, bao gồm test flow của backend2.
