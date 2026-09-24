# Backend 2 Implementation Guide

Tài liệu này giải thích chi tiết phần `backend2` đã được dựng lại trong mã nguồn hiện tại. Mục tiêu là để khi mở từng file, bạn biết ngay:

- File đó thuộc bài toán nào.
- Vì sao file đó tồn tại.
- Từng khối code chính đang xử lý điều gì.
- Các rule nghiệp vụ đang được giữ ở đâu.
- Cách các file nối với nhau thành một luồng hoàn chỉnh.

Backend 2 trong repo này được tổ chức theo style của backend1, tức là tách theo `feature package` thay vì gom tất cả controller/service/repository ở root.

## 1. Phạm vi backend2

Backend 2 hiện gồm 3 cụm nghiệp vụ chính:

- `feature/topic`: quản lý đề tài.
- `feature/groupregistration`: quản lý nhóm sinh viên và đăng ký đề tài.
- `feature/project`: giao đề tài chính thức cho nhóm.

Luồng tổng quát:

1. Giảng viên hoặc sinh viên đề xuất đề tài.
2. Giảng viên/admin duyệt đề tài.
3. Giảng viên/admin gán giảng viên hướng dẫn.
4. Đề tài được publish.
5. Sinh viên tạo nhóm.
6. Trưởng nhóm thêm/xóa thành viên.
7. Nhóm đăng ký đề tài.
8. Giảng viên/admin duyệt đăng ký.
9. Giảng viên/admin tạo quyết định giao đề tài.
10. Nhóm được khóa hoặc đóng tùy trạng thái assignment.

## 2. Cây thư mục backend2

```text
backend/src/main/java/com/example/qldetai/feature/
├─ topic/
│  ├─ ProposalSource.java
│  ├─ VisibilityType.java
│  ├─ TopicStatus.java
│  ├─ TopicReviewDecision.java
│  ├─ Topic.java
│  ├─ TopicSupervisor.java
│  ├─ TopicReview.java
│  ├─ TopicRepository.java
│  ├─ TopicSupervisorRepository.java
│  ├─ TopicReviewRepository.java
│  ├─ TopicDtos.java
│  ├─ TopicService.java
│  ├─ TopicController.java
│  └─ package-info.java
├─ groupregistration/
│  ├─ GroupStatus.java
│  ├─ GroupMemberRole.java
│  ├─ RegistrationStatus.java
│  ├─ StudentGroup.java
│  ├─ GroupMember.java
│  ├─ TopicRegistration.java
│  ├─ StudentGroupRepository.java
│  ├─ GroupMemberRepository.java
│  ├─ TopicRegistrationRepository.java
│  ├─ GroupRegistrationDtos.java
│  ├─ GroupService.java
│  ├─ RegistrationService.java
│  ├─ GroupController.java
│  ├─ RegistrationController.java
│  └─ package-info.java
└─ project/
   ├─ AssignmentStatus.java
   ├─ ProjectAssignment.java
   ├─ ProjectAssignmentRepository.java
   ├─ ProjectDtos.java
   ├─ ProjectAssignmentService.java
   ├─ ProjectAssignmentController.java
   └─ package-info.java
```

## 3. Gói `feature/topic`

### [`ProposalSource.java`](../backend/src/main/java/com/example/qldetai/feature/topic/ProposalSource.java)

Vai trò:

- Enum mô tả đề tài do ai đề xuất.

Giá trị:

- `STUDENT`: đề tài do sinh viên đề xuất.
- `LECTURER`: đề tài do giảng viên đề xuất.

Ý nghĩa trong flow:

- Khi tạo đề tài, `TopicService.determineProposalSource(...)` sẽ tự suy ra giá trị này từ profile người dùng.
- Giá trị này được trả ra API để frontend biết nguồn đề tài.

### [`VisibilityType.java`](../backend/src/main/java/com/example/qldetai/feature/topic/VisibilityType.java)

Vai trò:

- Enum mô tả đề tài là mở công khai hay chỉ dành riêng cho một nhóm.

Giá trị:

- `OPEN`: nhóm nào hợp lệ cũng có thể đăng ký.
- `RESERVED`: chỉ một nhóm cụ thể được phép đăng ký.

Điểm quan trọng:

- Nếu là `OPEN` thì `reservedGroupId` phải là `null`.
- Nếu là `RESERVED` thì bắt buộc phải có `reservedGroupId`.
- Rule này được giữ trong `TopicService.resolveReservedGroup(...)`.

### [`TopicStatus.java`](../backend/src/main/java/com/example/qldetai/feature/topic/TopicStatus.java)

Vai trò:

- Enum trạng thái vòng đời của đề tài.

Giá trị:

- `DRAFT`: dự thảo.
- `PENDING`: chờ duyệt.
- `REVISION_REQUIRED`: cần chỉnh sửa.
- `APPROVED`: đã duyệt.
- `PUBLISHED`: đã công bố.
- `REJECTED`: bị từ chối.
- `WITHDRAWN`: đã rút.

Ý nghĩa nghiệp vụ:

- Không phải trạng thái nào cũng được sửa.
- Không phải trạng thái nào cũng được publish.
- Các rule chuyển trạng thái được giữ trong `TopicService`.

### [`TopicReviewDecision.java`](../backend/src/main/java/com/example/qldetai/feature/topic/TopicReviewDecision.java)

Vai trò:

- Enum kết quả của một lần review đề tài.

Giá trị:

- `APPROVED`
- `REVISION_REQUIRED`
- `REJECTED`

Liên hệ với `TopicStatus`:

- Khi reviewer chọn một `TopicReviewDecision`, service sẽ map nó sang `TopicStatus` tương ứng.
- Mapping này được thực hiện ngay trong method `review(...)` của `TopicService`.

### [`Topic.java`](../backend/src/main/java/com/example/qldetai/feature/topic/Topic.java)

Vai trò:

- Entity đại diện cho bảng `topics`.
- Đây là object trung tâm của toàn bộ module đề tài.

Các nhóm field chính:

- Thông tin gắn đợt mở đề tài:
  - `period`
  - `faculty`
  - `department`
- Thông tin mô tả đề tài:
  - `code`
  - `title`
  - `description`
  - `objectives`
  - `expectedOutcomes`
  - `prerequisites`
- Thông tin người tạo:
  - `proposedBy`
  - `proposalSource`
- Thông tin khả năng đăng ký:
  - `visibilityType`
  - `reservedGroup`
- Thông tin vòng đời:
  - `status`
  - `publishedAt`
  - `createdAt`
  - `updatedAt`
  - `version`

Vì sao phải có cả `faculty` và `department` dù đã có `period`:

- Vì đề tài cần snapshot rõ nó thuộc đơn vị nào.
- Khi query hoặc hiển thị API sẽ không phải suy luận ngược quá nhiều.
- Rule người đề xuất và giảng viên hướng dẫn cùng khoa cũng dựa vào đây.

### [`TopicSupervisor.java`](../backend/src/main/java/com/example/qldetai/feature/topic/TopicSupervisor.java)

Vai trò:

- Entity map bảng `topic_supervisors`.
- Dùng để lưu danh sách giảng viên hướng dẫn của một đề tài.

Field chính:

- `topic`: đề tài nào.
- `lecturer`: giảng viên nào.
- `slotNo`: slot 1 hay slot 2.
- `assignedBy`: ai gán.
- `assignedAt`: gán lúc nào.

Ý nghĩa:

- Slot `1` được xem là giảng viên hướng dẫn chính.
- Slot `2` là đồng hướng dẫn.
- Khi publish đề tài, hệ thống bắt buộc phải có ít nhất một supervisor slot `1`.

### [`TopicReview.java`](../backend/src/main/java/com/example/qldetai/feature/topic/TopicReview.java)

Vai trò:

- Entity map bảng `topic_reviews`.
- Lưu lịch sử review đề tài.

Field chính:

- `topic`
- `reviewedBy`
- `decision`
- `comment`
- `reviewedAt`

Điểm đáng chú ý:

- `Topic` giữ trạng thái hiện tại.
- `TopicReview` giữ lịch sử từng lần reviewer nhận xét.
- `TopicService.toResponse(...)` chỉ lấy review mới nhất để trả ra `latestReview`.

### [`TopicRepository.java`](../backend/src/main/java/com/example/qldetai/feature/topic/TopicRepository.java)

Vai trò:

- Repository JPA cho entity `Topic`.

Method chính:

- `findByStatus(...)`: lọc theo trạng thái.
- `findByPeriodId(...)`: lọc theo đợt.
- `findByPeriodIdAndStatus(...)`: lọc theo đợt và trạng thái.
- `existsByPeriodIdAndCodeIgnoreCase(...)`: kiểm tra trùng mã đề tài trong cùng đợt.

Rule quan trọng:

- Mã đề tài là duy nhất theo từng `registration period`.
- Rule này được enforce ở service bằng method `existsByPeriodIdAndCodeIgnoreCase(...)`.

### [`TopicSupervisorRepository.java`](../backend/src/main/java/com/example/qldetai/feature/topic/TopicSupervisorRepository.java)

Vai trò:

- Repository cho `TopicSupervisor`.

Method chính:

- `findByTopicIdOrderBySlotNoAsc(...)`: lấy supervisor theo thứ tự slot.
- `deleteByTopicId(...)`: xóa toàn bộ supervisor cũ khi gán lại danh sách mới.

Ý tưởng thiết kế:

- Khi gán supervisor, code chọn cách xóa hết và tạo lại thay vì merge từng dòng cũ.
- Cách này đơn giản, dễ giữ đúng slot 1/2 và dễ đọc hơn.

### [`TopicReviewRepository.java`](../backend/src/main/java/com/example/qldetai/feature/topic/TopicReviewRepository.java)

Vai trò:

- Repository cho lịch sử review đề tài.

Method chính:

- `findByTopicIdOrderByReviewedAtDesc(...)`: lấy review mới nhất ở đầu danh sách.

Ứng dụng:

- `TopicService.toResponse(...)` dùng method này để dựng `latestReview`.

### [`TopicDtos.java`](../backend/src/main/java/com/example/qldetai/feature/topic/TopicDtos.java)

Vai trò:

- Gom toàn bộ request/response record của module đề tài vào một file.
- Cách này giúp package gọn và nhìn API contract tập trung hơn.

Các request record:

- `CreateTopicRequest`
  - Dùng khi tạo đề tài.
  - Có validation cho `periodId`, `code`, `title`, `description`, `objectives`, `expectedOutcomes`, `visibilityType`.
- `UpdateTopicRequest`
  - Dùng khi sửa nội dung đề tài.
  - Có `version` để chống ghi đè song song.
- `AssignSupervisorsRequest`
  - Gồm `version`, `primaryLecturerId`, `secondaryLecturerId`.
- `ReviewTopicRequest`
  - Gồm `version`, `decision`, `comment`.
- `PublishTopicRequest`
  - Chỉ cần `version`.
- `WithdrawTopicRequest`
  - Gồm `version`, `reason`.

Các response record:

- `TopicSupervisorResponse`
  - Dữ liệu ngắn gọn để frontend hiện danh sách supervisor.
- `TopicReviewResponse`
  - Dữ liệu tóm tắt review gần nhất.
- `TopicResponse`
  - Payload đầy đủ của đề tài.
  - Bao gồm thông tin period, faculty, department, proposer, status, supervisors, latestReview.

Ý nghĩa của `version`:

- Đây là optimistic locking.
- Khi client gửi request cập nhật, service kiểm tra `version` hiện tại của entity.
- Nếu version lệch, service ném `ConflictException`.

### [`TopicService.java`](../backend/src/main/java/com/example/qldetai/feature/topic/TopicService.java)

Vai trò:

- Đây là file xử lý nghiệp vụ trung tâm của module đề tài.
- Mọi rule chính đều tập trung ở đây.

#### 1. Khối dependency

Service inject các thành phần sau:

- `TopicRepository`
- `TopicSupervisorRepository`
- `TopicReviewRepository`
- `RegistrationPeriodRepository`
- `StudentGroupRepository`
- `StudentRepository`
- `LecturerRepository`
- `UserRepository`
- `NotificationService`
- `Clock`

Vì sao có `Clock`:

- Giúp phần test chủ động hơn khi cần kiểm soát thời gian.
- Constructor public dùng `Clock.systemUTC()`, nên runtime bình thường không phải cấu hình thêm.

#### 2. `list(...)`

Nhiệm vụ:

- Liệt kê đề tài theo phân trang.
- Cho phép lọc theo `periodId` và `status`.

Luồng:

1. Tạo `PageRequest`.
2. Chọn query phù hợp dựa trên filter có truyền hay không.
3. Map từng `Topic` sang `TopicResponse`.
4. Đóng gói thành `PageResponse`.

#### 3. `get(...)`

Nhiệm vụ:

- Lấy chi tiết một đề tài theo id.

Luồng:

1. Gọi `requireTopic(id)`.
2. Nếu không có thì ném `ResourceNotFoundException`.
3. Convert sang `TopicResponse`.

#### 4. `create(...)`

Đây là method quan trọng nhất khi khởi tạo đề tài.

Luồng xử lý:

1. Tải actor và `RegistrationPeriod`.
2. Xác định thời điểm hiện tại bằng `Clock`.
3. Suy ra `ProposalSource` từ user hiện tại.
4. Suy ra `Department` của actor.
5. Nếu actor không phải admin thì kiểm tra cửa sổ proposal còn mở hay không.
6. Kiểm tra khoa của actor có khớp khoa của period không.
7. Chuẩn hóa `code` sang uppercase.
8. Kiểm tra mã đề tài có bị trùng trong cùng period hay không.
9. Tạo entity `Topic`.
10. Gọi `apply(...)` để gán phần dữ liệu nội dung.
11. Set các field hệ thống như `proposedBy`, `proposalSource`, `status`, `createdAt`, `updatedAt`.
12. Save và trả ra response.

Điểm đáng chú ý:

- `apply(...)` được tách riêng để tránh lặp logic giữa `create(...)` và `update(...)`.
- Visibility `OPEN/RESERVED` được xử lý tại `resolveReservedGroup(...)`.

#### 5. `update(...)`

Nhiệm vụ:

- Sửa nội dung một đề tài đã tồn tại.

Luồng:

1. Tải `Topic` và `User`.
2. Kiểm tra `version`.
3. Kiểm tra quyền sửa bằng `ensureTopicEditor(...)`.
4. Gọi lại `apply(...)`.
5. Cập nhật `updatedAt`.
6. Trả response.

Rule chính:

- Chỉ proposer hoặc admin mới sửa được.
- Chỉ trạng thái `DRAFT`, `PENDING`, `REVISION_REQUIRED` mới được sửa.

#### 6. `assignSupervisors(...)`

Nhiệm vụ:

- Gán giảng viên hướng dẫn cho đề tài.

Luồng:

1. Tải topic và actor.
2. Kiểm tra `version`.
3. Kiểm tra actor phải là lecturer hoặc admin.
4. Chỉ cho phép gán supervisor khi đề tài đã `APPROVED` hoặc `PUBLISHED`.
5. Load giảng viên chính và giảng viên phụ.
6. Đảm bảo giảng viên phụ khác giảng viên chính.
7. Xóa toàn bộ supervisor cũ bằng `deleteByTopicId(...)`.
8. Tạo supervisor slot 1.
9. Nếu có supervisor phụ thì tạo slot 2.
10. Cập nhật `updatedAt`.
11. Trả response.

Rule phụ:

- `requireLecturer(...)` còn kiểm tra giảng viên phải cùng khoa với đề tài.

#### 7. `review(...)`

Nhiệm vụ:

- Ghi nhận kết quả review đề tài.

Luồng:

1. Tải topic và actor.
2. Kiểm tra `version`.
3. Kiểm tra actor có quyền moderator.
4. Không cho review tiếp nếu topic đã `PUBLISHED` hoặc `WITHDRAWN`.
5. Tạo dòng `TopicReview`.
6. Map `TopicReviewDecision` sang `TopicStatus`.
7. Cập nhật `updatedAt`.
8. Gửi notification cho người đề xuất.
9. Trả response.

Điểm hay:

- Lịch sử review được lưu riêng.
- Trạng thái hiện tại vẫn nằm ở bảng `topics`.

#### 8. `publish(...)`

Nhiệm vụ:

- Công bố đề tài cho sinh viên đăng ký.

Luồng:

1. Tải topic và actor.
2. Kiểm tra `version`.
3. Kiểm tra quyền moderator.
4. Chỉ topic `APPROVED` mới được publish.
5. Bắt buộc phải có supervisor slot 1.
6. Set `status = PUBLISHED`.
7. Set `publishedAt`, `updatedAt`.
8. Gửi notification cho proposer.
9. Trả response.

Rule nghiệp vụ:

- Duyệt xong nhưng chưa có supervisor thì vẫn chưa được publish.

#### 9. `withdraw(...)`

Nhiệm vụ:

- Rút đề tài.

Luồng:

1. Tải topic và actor.
2. Kiểm tra `version`.
3. Chỉ proposer hoặc admin được rút.
4. Không cho rút nếu đề tài đã publish.
5. Set `status = WITHDRAWN`.
6. Update thời gian.
7. Gửi notification.
8. Trả response.

#### 10. Các helper private

`apply(...)`

- Gom logic set các field text.
- Tách ra để `create` và `update` dùng chung.

`determineProposalSource(...)`

- Kiểm tra user có profile lecturer hay student.
- Từ đó suy ra nguồn đề tài.

`resolveDepartment(...)`

- Lấy department từ profile lecturer hoặc student.

`resolveReservedGroup(...)`

- Xử lý rule `OPEN` và `RESERVED`.
- Validate group có thuộc cùng period hay không.

`createSupervisor(...)`

- Hàm nhỏ để tạo entity `TopicSupervisor`.

`requireLecturer(...)`

- Load lecturer.
- Kiểm tra cùng khoa với đề tài.

`requirePeriod(...)`

- Load registration period.
- Bắt period phải `ACTIVE`.

`requireTopic(...)`, `requireUser(...)`

- Hàm tiện ích để load entity và ném lỗi chuẩn nếu không tồn tại.

`verifyVersion(...)`

- So sánh version request với version hiện tại trong DB.

`ensureTopicEditor(...)`

- Gom rule ai được sửa đề tài.

`ensureModerator(...)`

- Gom rule ai được review/publish/assign supervisor.

`isProposalWindowOpen(...)`

- Kiểm tra thời gian hiện tại có nằm trong khoảng proposal.

`hasRole(...)`

- Duyệt qua `userRoles` để check quyền.

`normalizeCode(...)`, `normalizeNullable(...)`

- Chuẩn hóa dữ liệu đầu vào trước khi lưu.

`toResponse(...)`

- Dựng `TopicResponse`.
- Đồng thời query supervisor và review mới nhất.

### [`TopicController.java`](../backend/src/main/java/com/example/qldetai/feature/topic/TopicController.java)

Vai trò:

- REST controller của module đề tài.

Endpoint chính:

- `GET /api/v1/topics`
  - Danh sách đề tài.
- `GET /api/v1/topics/{id}`
  - Chi tiết đề tài.
- `POST /api/v1/topics`
  - Tạo đề tài.
- `PUT /api/v1/topics/{id}`
  - Cập nhật đề tài.
- `PUT /api/v1/topics/{id}/supervisors`
  - Gán giảng viên hướng dẫn.
- `PATCH /api/v1/topics/{id}/review`
  - Review đề tài.
- `PATCH /api/v1/topics/{id}/publish`
  - Publish đề tài.
- `PATCH /api/v1/topics/{id}/withdraw`
  - Rút đề tài.

Điểm đáng chú ý:

- Controller chỉ làm 3 việc:
  - Nhận request.
  - Kiểm tra validation/security annotation.
  - Gọi service.
- Toàn bộ nghiệp vụ nằm ở service, controller không nhồi thêm rule.

### [`package-info.java`](../backend/src/main/java/com/example/qldetai/feature/topic/package-info.java)

Vai trò:

- Chỉ đánh dấu package `com.example.qldetai.feature.topic`.
- Comment placeholder cũ đã được bỏ để tránh gây hiểu nhầm là module này chưa làm.

## 4. Gói `feature/groupregistration`

### [`GroupStatus.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/GroupStatus.java)

Vai trò:

- Enum trạng thái của nhóm sinh viên.

Giá trị:

- `FORMING`: mới tạo, chưa đủ thành viên.
- `ACTIVE`: đã đủ để đi đăng ký đề tài.
- `LOCKED`: đã được giao đề tài, không đổi thành viên nữa.
- `CLOSED`: đề tài đã hoàn thành.

### [`GroupMemberRole.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/GroupMemberRole.java)

Vai trò:

- Enum vai trò thành viên trong nhóm.

Giá trị:

- `LEADER`
- `MEMBER`

Ý nghĩa:

- Nhiều action chỉ leader mới làm được.
- Ví dụ: thêm thành viên, xóa thành viên, đăng ký đề tài, rút đăng ký.

### [`RegistrationStatus.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/RegistrationStatus.java)

Vai trò:

- Enum trạng thái của một đơn đăng ký đề tài.

Giá trị:

- `PENDING`
- `APPROVED`
- `REJECTED`
- `WITHDRAWN`

### [`StudentGroup.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/StudentGroup.java)

Vai trò:

- Entity cho bảng `student_groups`.

Field chính:

- `period`: nhóm thuộc đợt nào.
- `name`: tên nhóm.
- `status`: trạng thái nhóm.
- `createdBy`: sinh viên tạo nhóm.
- `createdAt`
- `version`

Lưu ý:

- `createdBy` lưu theo `Student`, không lưu thẳng `User`.
- Điều này giúp nghiệp vụ nhóm luôn bám vào profile sinh viên thật.

### [`GroupMember.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/GroupMember.java)

Vai trò:

- Entity cho bảng `group_members`.

Field chính:

- `group`
- `period`
- `student`
- `slotNo`
- `memberRole`
- `joinedAt`
- `leftAt`

Ý nghĩa `leftAt`:

- Không xóa cứng thành viên khỏi lịch sử.
- Chỉ đánh dấu thời điểm rời nhóm.
- Khi query active member, code luôn lọc `leftAt is null`.

### [`TopicRegistration.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/TopicRegistration.java)

Vai trò:

- Entity cho bảng `topic_registrations`.

Field chính:

- `period`
- `group`
- `topic`
- `registeredBy`
- `motivation`
- `status`
- `registeredAt`
- `reviewedBy`
- `reviewedAt`
- `decisionReason`
- `version`

Ý nghĩa:

- Đây là bản ghi trung gian nối `StudentGroup` với `Topic`.
- Khi assignment được tạo, nó tham chiếu ngược về registration này.

### [`StudentGroupRepository.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/StudentGroupRepository.java)

Vai trò:

- Repository của `StudentGroup`.

Method chính:

- `findByPeriodId(...)`: list nhóm theo đợt.

### [`GroupMemberRepository.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/GroupMemberRepository.java)

Vai trò:

- Repository cho thành viên nhóm.

Method đáng chú ý:

- `findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(...)`
  - Lấy thành viên active theo thứ tự slot.
- `findByStudentIdAndLeftAtIsNullOrderByJoinedAtDesc(...)`
  - Dùng cho API `mine`.
- `findByGroupIdAndStudentIdAndLeftAtIsNull(...)`
  - Kiểm tra một sinh viên có đang ở trong nhóm hay không.
- `existsByPeriodIdAndStudentIdAndLeftAtIsNull(...)`
  - Kiểm tra một sinh viên đã thuộc nhóm active khác trong cùng period chưa.
- `countByGroupIdAndLeftAtIsNull(...)`
  - Dùng khi tính trạng thái nhóm.

### [`TopicRegistrationRepository.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/TopicRegistrationRepository.java)

Vai trò:

- Repository cho đăng ký đề tài.

Method chính:

- `findByStatus(...)`
- `findByPeriodId(...)`
- `findByPeriodIdAndStatus(...)`
- `existsByGroupIdAndStatusIn(...)`
  - Dùng để chặn nhóm gửi nhiều đơn song song.
- `findByTopicIdAndStatus(...)`
  - Dùng khi tạo assignment để tìm các đăng ký cùng topic.

### [`GroupRegistrationDtos.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/GroupRegistrationDtos.java)

Vai trò:

- Gom DTO của cả phần nhóm và phần đăng ký đề tài.

Nhóm DTO liên quan đến group:

- `CreateGroupRequest`
- `AddGroupMemberRequest`
- `GroupMemberResponse`
- `StudentGroupResponse`

Nhóm DTO liên quan đến topic registration:

- `CreateTopicRegistrationRequest`
- `ReviewTopicRegistrationRequest`
- `WithdrawTopicRegistrationRequest`
- `TopicRegistrationResponse`

Điểm đáng chú ý:

- `WithdrawTopicRegistrationRequest` bắt buộc `decisionReason` không được rỗng.
- `ReviewTopicRegistrationRequest` cho phép `decisionReason` rỗng nếu duyệt, nhưng service sẽ bắt buộc có lý do nếu từ chối.

### [`GroupService.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/GroupService.java)

Vai trò:

- Xử lý toàn bộ nghiệp vụ tạo nhóm, thêm thành viên, xóa thành viên, xem nhóm của tôi.

#### 1. `list(...)`

- List nhóm theo trang.
- Có thể lọc theo `periodId`.

#### 2. `mine(...)`

Nhiệm vụ:

- Trả danh sách nhóm active mà sinh viên hiện tại đang thuộc về.

Điểm hay:

- Dùng `LinkedHashMap` để tránh trùng id nhóm nếu query thành viên trả về nhiều dòng.

#### 3. `get(...)`

- Lấy chi tiết 1 nhóm.

#### 4. `create(...)`

Luồng:

1. Load `Student` từ actor.
2. Kiểm tra period có đang trong registration window hay không.
3. Kiểm tra sinh viên chưa thuộc nhóm active nào trong cùng period.
4. Kiểm tra sinh viên cùng khoa với period.
5. Tạo `StudentGroup` ở trạng thái `FORMING`.
6. Tạo luôn `GroupMember` cho leader slot `1`.
7. Trả response.

Ý nghĩa:

- Tạo nhóm là tạo cả nhóm lẫn bản ghi leader.
- Không có trạng thái nhóm “rỗng”.

#### 5. `addMember(...)`

Luồng:

1. Load actor và group.
2. Kiểm tra actor là leader của nhóm.
3. Kiểm tra nhóm còn được sửa (`ensureGroupMutable(...)`).
4. Tìm sinh viên theo `studentCode`.
5. Kiểm tra cùng khoa với period.
6. Kiểm tra sinh viên chưa ở nhóm active khác trong cùng period.
7. Lấy danh sách thành viên active hiện tại.
8. Nếu đã đủ 3 thì chặn.
9. Tìm `slotNo` trống đầu tiên bằng `nextAvailableSlot(...)`.
10. Tạo `GroupMember`.
11. Refresh trạng thái nhóm.
12. Gửi notification cho sinh viên vừa được thêm.
13. Trả response.

Điểm đã được sửa trong lượt này:

- Trước đây code lấy `slotNo = số lượng active member + 1`.
- Cách đó sẽ lỗi nếu xóa một thành viên ở giữa rồi thêm người mới, vì slot có thể bị trùng.
- Hiện tại code dùng `nextAvailableSlot(...)` để tái sử dụng đúng slot trống.

#### 6. `removeMember(...)`

Luồng:

1. Load actor và group.
2. Kiểm tra actor là leader.
3. Kiểm tra nhóm còn mutable.
4. Tìm active member cần xóa.
5. Chặn việc xóa leader.
6. Set `leftAt`.
7. Refresh trạng thái nhóm.
8. Trả response.

Thiết kế:

- Không xóa cứng member record.
- Nhờ đó vẫn giữ được lịch sử vào/ra nhóm.

#### 7. `ensureGroupMutable(...)`

Đây là nơi gom các rule khóa nhóm:

- Nếu group `LOCKED` hoặc `CLOSED` thì không sửa được.
- Nếu group đã có `ProjectAssignment` thì không sửa được.
- Nếu group đang có registration `PENDING` hoặc `APPROVED` thì không sửa được.

Nói ngắn gọn:

- Khi nhóm đã bước vào giai đoạn xét/được giao đề tài thì danh sách thành viên phải ổn định.

#### 8. `requireRegistrationWindow(...)`

- Load period.
- Kiểm tra period `ACTIVE`.
- Kiểm tra hiện tại đang trong khoảng `registrationStartAt` đến `registrationEndAt`.

#### 9. `requireLeader(...)`

- Xác nhận user hiện tại là thành viên active.
- Đồng thời phải có role `LEADER`.

#### 10. `requireGroup(...)`, `requireStudent(...)`

- Load entity và ném lỗi chuẩn nếu thiếu.
- `requireStudent(...)` còn kiểm tra user phải thật sự có role `STUDENT`.

#### 11. `refreshGroupStatus(...)`

- Nếu active member >= 2 thì `ACTIVE`.
- Nếu ít hơn 2 thì `FORMING`.

#### 12. `nextAvailableSlot(...)`

- Duyệt các slot từ 1 đến 3.
- Trả ra slot đầu tiên chưa dùng.
- Nếu hết slot thì ném `BusinessRuleException`.

#### 13. `toResponse(...)`

- Dựng `StudentGroupResponse`.
- Query thành viên active theo slot tăng dần.

### [`RegistrationService.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/RegistrationService.java)

Vai trò:

- Xử lý nghiệp vụ đăng ký đề tài của nhóm sinh viên.

#### 1. `list(...)`

- List topic registration theo trang.
- Lọc theo `periodId` và `status`.

#### 2. `get(...)`

- Lấy chi tiết một đơn đăng ký đề tài.

#### 3. `create(...)`

Luồng:

1. Load student actor, group, topic.
2. Kiểm tra actor là leader.
3. Kiểm tra nhóm đã `ACTIVE` và còn trong registration window.
4. Chặn nếu nhóm đã có assignment.
5. Chặn nếu nhóm đã có đơn `PENDING` hoặc `APPROVED`.
6. Kiểm tra topic và group thuộc cùng period.
7. Chỉ cho phép đăng ký topic đã `PUBLISHED`.
8. Nếu topic là `RESERVED` thì phải đúng nhóm được reserve.
9. Tạo `TopicRegistration`.
10. Set `status = PENDING`.
11. Save và trả response.

#### 4. `review(...)`

Luồng:

1. Load registration và reviewer.
2. Kiểm tra version.
3. Chỉ cho review khi trạng thái hiện tại là `PENDING`.
4. Chỉ chấp nhận trạng thái đích là `APPROVED` hoặc `REJECTED`.
5. Nếu `REJECTED` thì bắt buộc phải có `decisionReason`.
6. Set `status`, `reviewedBy`, `reviewedAt`, `decisionReason`.
7. Gửi notification cho toàn bộ thành viên active của nhóm.
8. Trả response.

#### 5. `withdraw(...)`

Luồng:

1. Load registration và actor.
2. Kiểm tra version.
3. Kiểm tra actor là leader.
4. Chỉ registration `PENDING` mới được rút.
5. Set `status = WITHDRAWN`.
6. Ghi `decisionReason`.
7. Trả response.

#### 6. `ensureGroupReady(...)`

- Chỉ nhóm `ACTIVE` mới được đăng ký đề tài.
- Đồng thời phải đang nằm trong cửa sổ đăng ký.

#### 7. `ensureLeader(...)`

- Chỉ leader mới được thao tác registration.

#### 8. `requireStudent(...)`, `requireReviewer(...)`

- `requireStudent(...)` yêu cầu role `STUDENT`.
- `requireReviewer(...)` chỉ cho `ADMIN` hoặc `LECTURER`.

#### 9. `verifyVersion(...)`

- Chống ghi đè song song khi review/rút registration.

#### 10. `normalizeNullable(...)`

- Chuẩn hóa text tùy chọn như `motivation`, `decisionReason`.

#### 11. `notifyGroup(...)`

- Lấy toàn bộ member active của group.
- Gửi notification cho từng người.

#### 12. `toResponse(...)`

- Trả đầy đủ thông tin group, topic, người đăng ký, người review và lý do quyết định.

### [`GroupController.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/GroupController.java)

Vai trò:

- REST controller cho nghiệp vụ nhóm.

Endpoint:

- `GET /api/v1/groups`
- `GET /api/v1/groups/mine`
- `GET /api/v1/groups/{id}`
- `POST /api/v1/groups`
- `POST /api/v1/groups/{id}/members`
- `DELETE /api/v1/groups/{id}/members/{studentId}`

Bảo mật:

- Tạo nhóm, xem nhóm của tôi, thêm/xóa thành viên đều yêu cầu `STUDENT`.

### [`RegistrationController.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/RegistrationController.java)

Vai trò:

- REST controller cho đăng ký đề tài.

Endpoint:

- `GET /api/v1/topic-registrations`
- `GET /api/v1/topic-registrations/{id}`
- `POST /api/v1/topic-registrations`
- `PATCH /api/v1/topic-registrations/{id}/review`
- `PATCH /api/v1/topic-registrations/{id}/withdraw`

Bảo mật:

- `POST` và `withdraw`: chỉ `STUDENT`.
- `review`: chỉ `ADMIN` hoặc `LECTURER`.

### [`package-info.java`](../backend/src/main/java/com/example/qldetai/feature/groupregistration/package-info.java)

Vai trò:

- Chỉ giữ khai báo package.
- Comment placeholder cũ đã bỏ.

## 5. Gói `feature/project`

### [`AssignmentStatus.java`](../backend/src/main/java/com/example/qldetai/feature/project/AssignmentStatus.java)

Vai trò:

- Enum trạng thái quyết định giao đề tài.

Giá trị:

- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`

### [`ProjectAssignment.java`](../backend/src/main/java/com/example/qldetai/feature/project/ProjectAssignment.java)

Vai trò:

- Entity cho bảng `project_assignments`.

Field chính:

- `registration`
- `group`
- `topic`
- `period`
- `assignedBy`
- `assignedAt`
- `status`
- `version`

Ý nghĩa:

- Đây là bản ghi xác nhận cuối cùng rằng đề tài đã được giao cho nhóm.
- Nó liên kết thẳng về registration đã được duyệt.

### [`ProjectAssignmentRepository.java`](../backend/src/main/java/com/example/qldetai/feature/project/ProjectAssignmentRepository.java)

Vai trò:

- Repository cho `ProjectAssignment`.

Method chính:

- `findByStatus(...)`
- `findByPeriodId(...)`
- `findByPeriodIdAndStatus(...)`
- `existsByRegistrationId(...)`
- `existsByGroupId(...)`
- `existsByTopicId(...)`

Tác dụng:

- Chặn giao trùng assignment cho cùng registration, cùng group, hoặc cùng topic.

### [`ProjectDtos.java`](../backend/src/main/java/com/example/qldetai/feature/project/ProjectDtos.java)

Vai trò:

- DTO của module assignment.

Request:

- `CreateAssignmentRequest`
  - Chỉ cần `registrationId`.
- `UpdateAssignmentStatusRequest`
  - Cần `version` và trạng thái mới.

Response:

- `ProjectAssignmentResponse`
  - Trả đủ dữ liệu để frontend hiển thị quyết định giao đề tài.

### [`ProjectAssignmentService.java`](../backend/src/main/java/com/example/qldetai/feature/project/ProjectAssignmentService.java)

Vai trò:

- Xử lý tạo assignment và đổi trạng thái assignment.

#### 1. `TRANSITIONS`

Đây là map rule chuyển trạng thái:

- `IN_PROGRESS -> COMPLETED, CANCELLED`
- `COMPLETED -> không đi đâu nữa`
- `CANCELLED -> không đi đâu nữa`

Ý nghĩa:

- Tránh việc assignment nhảy trạng thái lung tung.

#### 2. `list(...)`

- List assignment theo trang.
- Lọc theo `periodId` và `status`.

#### 3. `get(...)`

- Lấy chi tiết 1 assignment.

#### 4. `create(...)`

Đây là bước chốt cuối cùng của luồng backend2.

Luồng:

1. Xác minh actor là manager (`ADMIN` hoặc `LECTURER`).
2. Load registration.
3. Chỉ registration `APPROVED` mới được tạo assignment.
4. Chặn nếu registration này đã có assignment.
5. Chặn nếu group đã có assignment.
6. Chặn nếu topic đã có assignment.
7. Tạo `ProjectAssignment` với `status = IN_PROGRESS`.
8. Set group thành `LOCKED`.
9. Tìm các registration `PENDING` khác của cùng topic.
10. Chuyển các registration đó sang `REJECTED`.
11. Gửi notification cho thành viên nhóm được giao đề tài.
12. Trả response.

Ý nghĩa nghiệp vụ:

- Một topic khi đã được giao cho một nhóm thì những đăng ký chờ khác của topic đó bị loại.
- Nhóm đã được giao đề tài thì không còn đổi thành viên được nữa.

#### 5. `updateStatus(...)`

Luồng:

1. Load assignment.
2. Kiểm tra actor có quyền manager.
3. Kiểm tra version.
4. Kiểm tra có được phép đổi từ trạng thái hiện tại sang trạng thái mới hay không.
5. Set trạng thái mới.
6. Nếu `COMPLETED` thì group -> `CLOSED`.
7. Nếu `CANCELLED` thì mở lại group về `ACTIVE` hoặc `FORMING` tùy số thành viên active.
8. Gửi notification cho nhóm.
9. Trả response.

#### 6. `requireRegistration(...)`, `requireAssignment(...)`, `requireManager(...)`

- Gom logic load entity và kiểm tra quyền.

#### 7. `verifyVersion(...)`

- Chống ghi đè song song khi đổi trạng thái assignment.

#### 8. `notifyGroup(...)`

- Gửi thông báo tới từng thành viên active của nhóm.

#### 9. `toResponse(...)`

- Dựng `ProjectAssignmentResponse` từ entity.

### [`ProjectAssignmentController.java`](../backend/src/main/java/com/example/qldetai/feature/project/ProjectAssignmentController.java)

Vai trò:

- REST controller cho module assignment.

Endpoint:

- `GET /api/v1/project-assignments`
- `GET /api/v1/project-assignments/{id}`
- `POST /api/v1/project-assignments`
- `PATCH /api/v1/project-assignments/{id}/status`

Bảo mật:

- Tạo assignment và đổi trạng thái chỉ dành cho `ADMIN` hoặc `LECTURER`.

### [`package-info.java`](../backend/src/main/java/com/example/qldetai/feature/project/package-info.java)

Vai trò:

- Chỉ giữ khai báo package.

## 6. Liên kết giữa ba module

Đây là chỗ quan trọng nhất nếu muốn hiểu kiến trúc tổng thể:

- `topic` sinh ra đề tài để sinh viên có thứ mà đăng ký.
- `groupregistration` sinh ra nhóm và đơn đăng ký đề tài.
- `project` chốt đơn đăng ký đã được duyệt thành assignment chính thức.

Quan hệ dữ liệu:

- `RegistrationPeriod` 1-n `Topic`
- `RegistrationPeriod` 1-n `StudentGroup`
- `StudentGroup` 1-n `GroupMember`
- `StudentGroup` 1-n `TopicRegistration`
- `Topic` 1-n `TopicReview`
- `Topic` 1-n `TopicSupervisor`
- `Topic` 1-n `TopicRegistration`
- `TopicRegistration` 1-1 `ProjectAssignment`

## 7. Rule nghiệp vụ đang nằm ở đâu

Nếu cần tìm nhanh “rule này ở file nào”, xem bảng sau:

- Chỉ tạo đề tài trong cửa sổ proposal:
  - `TopicService.isProposalWindowOpen(...)`
- Mã đề tài không trùng trong cùng period:
  - `TopicService.create(...)`
- Chỉ topic đã duyệt mới publish:
  - `TopicService.publish(...)`
- Publish phải có supervisor chính:
  - `TopicService.publish(...)`
- Mỗi sinh viên chỉ ở một nhóm active trong cùng period:
  - `GroupService.create(...)`
  - `GroupService.addMember(...)`
- Nhóm tối đa 3 thành viên:
  - `GroupService.addMember(...)`
- Chỉ leader mới quản lý nhóm:
  - `GroupService.requireLeader(...)`
- Nhóm có assignment hoặc registration đang xét thì không đổi thành viên:
  - `GroupService.ensureGroupMutable(...)`
- Chỉ nhóm active mới đăng ký topic:
  - `RegistrationService.ensureGroupReady(...)`
- Chỉ topic đã publish mới được đăng ký:
  - `RegistrationService.create(...)`
- Topic reserved chỉ nhóm đúng mới được đăng ký:
  - `RegistrationService.create(...)`
- Chỉ registration approved mới tạo assignment:
  - `ProjectAssignmentService.create(...)`
- Một topic/group/registration không được có assignment trùng:
  - `ProjectAssignmentService.create(...)`
- Assignment hoàn thành thì group đóng:
  - `ProjectAssignmentService.updateStatus(...)`

## 8. Test đã thêm cho backend2

File test:

- [`ProjectLifecycleIntegrationTests.java`](../backend/src/test/java/com/example/qldetai/workflow/ProjectLifecycleIntegrationTests.java)

Các luồng được cover:

- Vòng đời đề tài từ tạo -> review -> assign supervisor -> publish.
- Tái sử dụng `slotNo` khi xóa thành viên rồi thêm người mới.
- Đăng ký đề tài và duyệt đăng ký.
- Tạo assignment, auto reject đăng ký pending khác của cùng topic, và đóng nhóm khi completed.

Ý nghĩa:

- Những chỗ dễ lỗi nhất của backend2 giờ đã có test khóa lại.
- Sau này nếu sửa service mà làm lệch rule, test sẽ báo ngay.

## 9. Gợi ý đọc code theo thứ tự

Nếu bạn muốn đọc để hiểu nhanh backend2, nên đi theo thứ tự:

1. Đọc `TopicController` -> `TopicService` -> entity/DTO/repository liên quan.
2. Đọc `GroupController` -> `GroupService`.
3. Đọc `RegistrationController` -> `RegistrationService`.
4. Đọc `ProjectAssignmentController` -> `ProjectAssignmentService`.
5. Đọc file test `ProjectLifecycleIntegrationTests.java` để nhìn luồng end-to-end.

Nếu bạn muốn chuẩn bị cho phần báo cáo hoặc thuyết trình, chỉ cần bám 4 flow sau:

1. Tạo và công bố đề tài.
2. Tạo nhóm và quản lý thành viên.
3. Đăng ký đề tài và duyệt đăng ký.
4. Giao đề tài và cập nhật kết quả assignment.
