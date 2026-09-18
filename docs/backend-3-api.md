# Backend 3 — hợp đồng API và payload mẫu

Tài liệu này mô tả các API của phần `backend3` theo đúng code hiện tại. Mục tiêu là để frontend, người test API, hoặc người thuyết trình có thể nhìn nhanh:

- Endpoint nào đang có.
- Endpoint đó cần quyền gì.
- Payload request mẫu trông như thế nào.
- Response thực tế được trả về theo format nào.

Base URL mặc định: `/api/v1`.

## Phạm vi backend3

Backend 3 hiện xử lý 3 mảng:

- Theo dõi tiến độ, báo cáo và nộp file.
- Hội đồng, lịch bảo vệ và phân công chấm.
- Phiếu chấm, kết quả và yêu cầu đổi điểm.

## Phụ thuộc xác thực

Backend 3 dùng chung cơ chế session và CSRF của backend1.

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
  "timestamp": "2026-09-18T16:00:00Z"
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
  "timestamp": "2026-09-18T16:00:00Z"
}
```

## Enum thường gặp

### Tiến độ

- `MilestoneType`: `PROPOSAL`, `PROGRESS`, `FINAL`
- `MilestoneStatus`: `OPEN`, `SUBMITTED`, `REVISION_REQUIRED`, `ACCEPTED`
- `StoredFileStatus`: `STAGED`, `READY`, `DELETED`

### Đánh giá

- `AssessmentComponentType`: `REVIEW`, `COMMITTEE`, `OTHER`
- `EvaluationStatus`: `ACTIVE`, `CANCELLED`
- `ScoreSheetStatus`: `DRAFT`, `SUBMITTED`, `LOCKED`
- `CommitteeRole`: `CHAIR`, `SECRETARY`, `MEMBER`
- `CommitteeStatus`: `DRAFT`, `CONFIRMED`, `COMPLETED`
- `DefenseStatus`: `SCHEDULED`, `COMPLETED`, `CANCELLED`
- `ResultStatus`: `FINALIZED`, `PUBLISHED`, `SUPERSEDED`
- `GradeChangeStatus`: `PENDING`, `APPROVED`, `REJECTED`, `APPLIED`

## 1. API tệp và tiến độ

### Danh sách endpoint

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| POST | `/files` | Đã đăng nhập | Upload file và nhận `fileId` |
| GET | `/files/{id}` | Đã đăng nhập | Lấy metadata file đã upload |
| POST | `/milestones` | `ADMIN`, `LECTURER` | Tạo mốc tiến độ cho assignment |
| GET | `/project-assignments/{assignmentId}/milestones` | Đã đăng nhập | Danh sách milestone của assignment |
| POST | `/milestones/{id}/submissions` | `STUDENT` | Nộp báo cáo vào milestone |
| POST | `/report-submissions/{id}/feedback` | `ADMIN`, `LECTURER` | Phản hồi báo cáo đã nộp |

### POST `/files`

Request dùng `multipart/form-data`, field bắt buộc là `file`.

Ví dụ:

```http
POST /api/v1/files
Content-Type: multipart/form-data
```

Response mẫu:

```json
{
  "success": true,
  "message": "File uploaded.",
  "data": {
    "id": 501,
    "storageKey": "20260918/550e8400-e29b-41d4-a716-446655440000-report.pdf",
    "originalName": "report.pdf",
    "mimeType": "application/pdf",
    "sizeBytes": 248231,
    "sha256": "4f8d4f1c1d5b2b1e1e2c8d62b2d5d0f2f1cb8d507f0bc2bc8d9b32f82f8f8073",
    "uploadedAt": "2026-09-18T16:20:00",
    "status": "READY"
  },
  "timestamp": "2026-09-18T16:20:00Z"
}
```

Lưu ý:

- API này chỉ trả metadata, chưa trả file nhị phân để download.
- Frontend phải upload file trước, sau đó lấy `data.id` để truyền vào `POST /milestones/{id}/submissions`.
- Có thể đổi thư mục lưu file bằng biến môi trường `FILE_STORAGE_ROOT`. Nếu không cấu hình, backend dùng thư mục tạm của JVM.

### POST `/milestones`

Request mẫu:

```json
{
  "assignmentId": 300,
  "title": "Báo cáo tiến độ tuần 3",
  "milestoneType": "PROGRESS",
  "description": "Nộp báo cáo và demo chức năng đã hoàn thành.",
  "dueAt": "2026-09-28T17:00:00"
}
```

Rule:

- Chỉ `ADMIN` hoặc `LECTURER` tạo được milestone.
- `dueAt` phải là thời điểm trong tương lai.

### POST `/milestones/{id}/submissions`

Request mẫu:

```json
{
  "fileId": 501,
  "note": "Nộp bản báo cáo tiến độ tuần 3."
}
```

Rule:

- `fileId` phải là file đã upload thành công qua `/files`.
- Sinh viên chỉ nộp được cho assignment thuộc nhóm của mình.
- Mỗi lần nộp sẽ tạo một `SubmissionResponse` mới với `versionNo` tăng dần.

### POST `/report-submissions/{id}/feedback`

Request mẫu:

```json
{
  "content": "Nội dung ổn, cần bổ sung phần demo dữ liệu thực tế.",
  "accepted": false
}
```

Rule:

- Chỉ `ADMIN` hoặc `LECTURER` được phản hồi.
- `accepted = true` sẽ đẩy milestone sang trạng thái đạt; `accepted = false` dùng cho yêu cầu chỉnh sửa.

## 2. API đánh giá, hội đồng và kết quả

### 2.1. Danh mục chấm điểm

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/assessment-components` | Đã đăng nhập | Danh sách thành phần chấm |
| POST | `/assessment-components` | `ADMIN`, `LECTURER` | Tạo thành phần chấm |
| PATCH | `/assessment-components/{id}/lock` | `ADMIN`, `LECTURER` | Khóa thành phần chấm |
| GET | `/rubrics` | Đã đăng nhập | Danh sách rubric |
| POST | `/rubrics` | `ADMIN`, `LECTURER` | Tạo rubric |
| PATCH | `/rubrics/{id}/lock` | `ADMIN`, `LECTURER` | Khóa rubric |

### POST `/assessment-components`

Request mẫu:

```json
{
  "periodId": 1,
  "code": "PB1",
  "name": "Chấm phản biện",
  "componentType": "REVIEW",
  "expectedEvaluators": 1
}
```

Rule:

- `COMMITTEE` bắt buộc `expectedEvaluators` từ `3` đến `5`.
- `code` được backend chuẩn hóa lên chữ hoa.

### POST `/rubrics`

Request mẫu:

```json
{
  "componentId": 10,
  "name": "Rubric phản biện v1",
  "criteria": [
    {
      "name": "Nội dung",
      "description": "Đánh giá chất lượng nội dung",
      "maxScore": 6.0,
      "sortOrder": 1
    },
    {
      "name": "Trình bày",
      "description": "Đánh giá cách trình bày",
      "maxScore": 4.0,
      "sortOrder": 2
    }
  ]
}
```

Rule khóa:

- Rubric phải có ít nhất một tiêu chí.
- Tổng `maxScore` của các tiêu chí phải đúng bằng `10` trước khi gọi `PATCH /rubrics/{id}/lock`.
- Chỉ khi đã có ít nhất một rubric `LOCKED` thì mới được `PATCH /assessment-components/{id}/lock`.

### 2.2. Gán evaluator và nhập phiếu chấm

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/evaluation-assignments` | `ADMIN`, `LECTURER` | Danh sách evaluator assignment |
| POST | `/evaluation-assignments` | `ADMIN`, `LECTURER` | Gán evaluator cho assignment |
| GET | `/evaluation-assignments/{id}/score-sheet` | `LECTURER` | Phiếu chấm của chính evaluator |
| PUT | `/evaluation-assignments/{id}/score-sheet` | `LECTURER` | Lưu hoặc nộp phiếu chấm |

### POST `/evaluation-assignments`

Request mẫu cho phản biện:

```json
{
  "assignmentId": 300,
  "rubricId": 22,
  "lecturerId": 14,
  "defenseId": null,
  "deadlineAt": "2026-10-01T17:00:00"
}
```

Request mẫu cho chấm hội đồng:

```json
{
  "assignmentId": 300,
  "rubricId": 35,
  "lecturerId": 18,
  "defenseId": 77,
  "deadlineAt": "2026-10-15T17:00:00"
}
```

Rule:

- Giảng viên hướng dẫn của đề tài không được chấm chính đề tài đó.
- `REVIEW`: `defenseId` phải là `null`, backend gán `evaluatorRole = REVIEWER`.
- `COMMITTEE`: `defenseId` là bắt buộc và giảng viên phải là thành viên của hội đồng trong defense đó; backend gán `evaluatorRole = COMMITTEE_MEMBER`.
- `OTHER`: không nhận `defenseId`.

### PUT `/evaluation-assignments/{id}/score-sheet`

Request mẫu lưu nháp:

```json
{
  "version": 0,
  "overallComment": "Nhận xét sơ bộ.",
  "scores": [
    {
      "criterionId": 101,
      "score": 5.5,
      "comment": "Phần nội dung ổn."
    },
    {
      "criterionId": 102,
      "score": 3.5,
      "comment": "Cần trình bày rõ hơn."
    }
  ],
  "submit": false
}
```

Lưu ý:

- Mỗi tiêu chí trong rubric phải có điểm.
- Điểm từng tiêu chí không được vượt `maxScore`.
- `submit = true` sẽ chuyển phiếu sang `SUBMITTED`.
- Sau khi kết quả được finalize, backend khóa phiếu chấm thành `LOCKED`.

### 2.3. Hội đồng, defense và kết quả

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| POST | `/evaluation-committees` | `ADMIN`, `LECTURER` | Tạo hội đồng |
| GET | `/evaluation-committees` | Đã đăng nhập | Danh sách hội đồng |
| POST | `/evaluation-committees/{id}/members` | `ADMIN`, `LECTURER` | Thêm thành viên hội đồng |
| POST | `/defense-sessions` | `ADMIN`, `LECTURER` | Xếp lịch bảo vệ |
| GET | `/defense-sessions` | Đã đăng nhập | Danh sách buổi bảo vệ |
| POST | `/project-assignments/{id}/results/finalize` | `ADMIN`, `LECTURER` | Chốt kết quả |
| GET | `/results` | `ADMIN`, `LECTURER` | Danh sách kết quả |
| GET | `/results/{id}` | Đã đăng nhập | Chi tiết kết quả |
| POST | `/results/{id}/publish` | `ADMIN`, `LECTURER` | Công bố kết quả |

### POST `/evaluation-committees/{id}/members`

Request mẫu:

```json
{
  "lecturerId": 18,
  "slotNo": 1,
  "role": "CHAIR"
}
```

Rule:

- Hội đồng có từ `3` đến `5` thành viên.
- Không trùng `slotNo`, không trùng giảng viên.
- `CHAIR` và `SECRETARY` mỗi loại chỉ có một người.

### POST `/defense-sessions`

Request mẫu:

```json
{
  "assignmentId": 300,
  "committeeId": 41,
  "location": "Phòng A101",
  "startsAt": "2026-10-15T08:00:00",
  "endsAt": "2026-10-15T09:00:00"
}
```

Rule:

- `startsAt` phải trước `endsAt`.
- Hội đồng phải đủ 3-5 người, có `CHAIR` và `SECRETARY`.
- Giảng viên hướng dẫn không được nằm trong hội đồng bảo vệ của chính đề tài.
- Khi xếp lịch thành công, backend chuyển `committee.status` sang `CONFIRMED`.

### POST `/project-assignments/{id}/results/finalize`

Rule:

- Mỗi thành phần chấm phải có đủ số phiếu `SUBMITTED` theo `expectedEvaluators`.
- Backend tính `finalScore` bằng trung bình cộng các tổng điểm phiếu chấm active hiện có.
- Sau khi finalize, các `score-sheet` liên quan bị khóa.

### GET `/results/{id}`

Quyền xem:

- `ADMIN`, `LECTURER`: xem mọi kết quả.
- `STUDENT`: chỉ xem được khi kết quả đã `PUBLISHED` và sinh viên thuộc nhóm của assignment đó.

### 2.4. Yêu cầu đổi điểm

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| POST | `/score-sheets/{id}/grade-change-requests` | `LECTURER` | Tạo yêu cầu mở lại phiếu chấm |
| GET | `/grade-change-requests` | `ADMIN`, `LECTURER` | Danh sách yêu cầu đổi điểm |
| PATCH | `/grade-change-requests/{id}/review` | `ADMIN`, `LECTURER` | Duyệt hoặc từ chối yêu cầu |

### PATCH `/grade-change-requests/{id}/review`

Request mẫu:

```json
{
  "approved": true,
  "comment": "Cho phép mở lại phiếu để sửa điểm."
}
```

Flow thực tế:

1. Giảng viên chỉ tạo được yêu cầu khi phiếu chấm đang `LOCKED`.
2. Nếu duyệt `approved = true`, backend chuyển request sang `APPROVED` và mở lại `score-sheet` về `DRAFT`.
3. Lần lưu phiếu tiếp theo của evaluator sẽ tự động đổi request sang `APPLIED` và ghi `appliedAt`.

## 3. Danh sách field response chính

### `StoredFileResponse`

- `id`
- `storageKey`
- `originalName`
- `mimeType`
- `sizeBytes`
- `sha256`
- `uploadedAt`
- `status`

### `MilestoneResponse`

- `id`
- `assignmentId`
- `title`
- `milestoneType`
- `description`
- `dueAt`
- `status`
- `version`
- `submissions`

### `SubmissionResponse`

- `id`
- `versionNo`
- `file`
- `submittedBy`, `submittedByName`
- `note`
- `submittedAt`
- `feedback`

### `AssessmentComponentResponse`

- `id`
- `periodId`, `periodName`
- `code`, `name`
- `componentType`
- `expectedEvaluators`
- `status`

### `RubricResponse`

- `id`
- `componentId`, `componentCode`, `componentName`
- `name`
- `versionNo`
- `status`
- `criteria`

### `EvaluationAssignmentResponse`

- `id`
- `assignmentId`
- `periodId`, `periodName`
- `componentId`, `componentCode`, `componentName`
- `rubricId`, `rubricName`, `rubricVersionNo`
- `lecturerId`, `lecturerCode`, `lecturerName`
- `evaluatorRole`
- `defenseId`
- `groupId`, `groupName`
- `topicId`, `topicCode`, `topicTitle`
- `status`
- `deadlineAt`
- `scoreSheetId`
- `scoreSheetStatus`

### `EvaluationCommitteeResponse`

- `id`
- `periodId`, `periodName`
- `facultyId`, `facultyName`
- `name`
- `status`
- `createdBy`, `createdByName`
- `createdAt`
- `version`
- `members`

### `DefenseSessionResponse`

- `id`
- `assignmentId`
- `groupId`, `groupName`
- `topicId`, `topicCode`, `topicTitle`
- `committeeId`, `committeeName`
- `periodId`, `periodName`
- `location`
- `startsAt`, `endsAt`
- `status`
- `version`

### `ResultDetailResponse`

- `id`
- `assignmentId`
- `groupId`, `groupName`
- `topicId`, `topicCode`, `topicTitle`
- `periodId`, `periodName`
- `versionNo`
- `finalScore`
- `status`
- `finalizedBy`, `finalizedByName`, `finalizedAt`
- `publishedBy`, `publishedByName`, `publishedAt`

### `GradeChangeRequestResponse`

- `id`
- `sheetId`
- `evaluationId`
- `assignmentId`
- `requestedBy`, `requestedByName`
- `reason`
- `status`
- `reviewedBy`, `reviewedByName`
- `reviewComment`
- `createdAt`, `reviewedAt`, `appliedAt`

## 4. Lỗi thường gặp

- `400 Bad Request`
  - Upload file rỗng.
  - Rubric không đủ 10 điểm.
  - Hội đồng không đủ điều kiện để xếp lịch.
  - Thiếu `defenseId` cho component kiểu `COMMITTEE`.
- `401 Unauthorized`
  - Chưa đăng nhập hoặc session hết hạn.
- `403 Forbidden`
  - Sinh viên nộp báo cáo không thuộc assignment đó.
  - Giảng viên xem hoặc sửa phiếu chấm không phải của mình.
- `404 Not Found`
  - Không tìm thấy file, milestone, evaluation, defense, result hoặc grade-change request.
- `409 Conflict`
  - Trùng evaluator, trùng hội đồng hoặc `version` cũ hơn dữ liệu hiện tại.

## 5. Trình tự gọi API khuyến nghị

Flow chuẩn khi demo hoặc tích hợp:

1. Đăng nhập.
2. Upload file bằng `POST /files` khi cần nộp báo cáo.
3. Tạo milestone bằng `POST /milestones`.
4. Sinh viên nộp báo cáo bằng `POST /milestones/{id}/submissions`.
5. Giảng viên phản hồi bằng `POST /report-submissions/{id}/feedback`.
6. Tạo `assessment-component`.
7. Tạo `rubric`.
8. Khóa rubric rồi khóa component.
9. Nếu cần bảo vệ, tạo hội đồng, thêm thành viên, xếp `defense-session`.
10. Gán evaluator bằng `POST /evaluation-assignments`.
11. Giảng viên lưu hoặc nộp `score-sheet`.
12. Chốt kết quả bằng `POST /project-assignments/{id}/results/finalize`.
13. Công bố kết quả bằng `POST /results/{id}/publish`.
14. Nếu cần sửa điểm sau khi khóa phiếu, dùng flow `grade-change-request`.

## 6. Kiểm thử nhanh bằng Maven

```powershell
cd backend
mvn test
```

Tại thời điểm dựng tài liệu này, backend đã pass toàn bộ test hiện có, bao gồm test workflow cho upload file, `/auth/me`, lecturer directory và luồng đánh giá/đổi điểm.
