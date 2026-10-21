# Rà soát backend — 21/10/2026

## Phạm vi và nguyên tắc

Đọc Controller, Service, Repository, entity, schema MySQL và test thực tế. Workspace đã có thay đổi chưa commit trước khi bắt đầu; báo cáo phân biệt phần có sẵn và phần sửa bổ sung. Không sửa frontend, DTO, endpoint, quyền truy cập; không kết nối hoặc thay dữ liệu production. Test dùng H2 riêng.

## Giai đoạn 1 — Các phát hiện trước khi sửa

| File / phương thức | Bằng chứng và tác động | Mức độ | Hướng sửa tối thiểu |
|---|---|---|---|
| `EvaluationService.createRubric` | `countByComponentId + 1` không khóa; hai request chọn cùng phiên bản; số phiên bản sai nếu lịch sử có khoảng trống. SQL đã có `uq_rubrics_01`, entity chưa phản ánh. | Cao | Khóa component, đọc phiên bản lớn nhất bằng current locking read; dùng max + 1; ánh xạ unique hiện có. |
| `EvaluationService.assign` | Có khóa project nhưng truy vấn đếm thường sau `manager()` có thể dùng snapshot cũ dưới MySQL REPEATABLE READ. Chưa kiểm tra trùng người chấm tại Service. | Cao | Đọc danh sách ACTIVE có khóa sau khóa project, kiểm tra số lượng và giảng viên; giữ quy tắc của unique SQL hiện có. |
| `EvaluationService.saveSheet` | Hai lần lưu đầu cùng evaluation có thể cùng thấy chưa có sheet; sửa detail chỉ được bảo vệ gián tiếp khi sheet có cập nhật/version. | Cao | Khóa evaluation trước khi đọc/ghi sheet, current read sheet; giữ kiểm tra version. |
| `GroupService.removeMember` | Không khóa group như create registration/add member; xóa thành viên có thể chạy song song với đăng ký, làm nhóm đã đăng ký bị thay thành viên. | Cao | Dùng cùng khóa group; kiểm tra các đăng ký hiện hành bằng current read. |
| `RegistrationService.create`, `GroupService`, `GroupInvitationService` | Có khóa nhóm/sinh viên và query membership có khóa; các kiểm tra registration bằng `exists` vẫn có nguy cơ snapshot cũ sau khi chờ khóa nhóm. | Cao | Current read đăng ký đang PENDING/APPROVED khi đang giữ khóa nhóm. Không unique toàn bộ lịch sử đăng ký. |
| `ProjectAssignmentService.create` | `findByRegistrationId` rồi insert: retry đồng thời có thể lỗi unique thay vì trả phân công hiện có. SQL unique group/topic/registration đã có; entity chỉ thể hiện one-to-one registration. | Cao | Tuần tự hóa theo registration, current read phân công; ánh xạ unique group/topic đã tồn tại. Giữ MANDATORY và rollback chung duyệt–phân công. |
| `EvaluationCommitteeService.addMember`, `DefenseSessionService.schedule` | Đọc–kiểm tra–ghi hội đồng/lịch không dùng chung khóa; lịch trùng khoảng thời gian không được unique bảo vệ. `addMember` còn đọc danh sách hai lần. | Cao | Khóa committee và project tại thao tác lịch; current read lịch xung đột; dùng lại danh sách thành viên. |
| `EvaluationResultService.finalizeResult` | `count + 1` và kiểm tra FINALIZED không khóa; có thể tạo trùng phiên bản hoặc nhiều bản chờ công bố. | Cao | Khóa project và đọc các phiên bản hiện tại có khóa; max + 1. |
| `GradeChangeService.reviewChange` | GradeChangeRequest không có version; hai quyết định đồng thời có thể ghi đè. | Cao | Khóa request khi review; giữ thứ tự flush trước khi mở lại sheet. |
| `GlobalExceptionHandler` | Chưa ánh xạ optimistic/pessimistic concurrency exceptions; xung đột có thể thành HTTP 500. | Trung bình | Trả 409 với ApiResponse và thông báo chung, không lộ SQL/stack trace. |
| `EvaluationService.listRubrics/listAssignments` | Query criteria/sheet riêng trong mỗi DTO; rubric/component/assignment/lecturer là LAZY. | Trung bình | Lấy children theo tập ID của trang/list, fetch to-one; giữ List/PageResponse hiện tại. |
| `GroupService.list`, `EvaluationCommitteeService.listCommittees` | Query members cho từng group/committee trong trang. | Trung bình | Batch children theo ID; fetch quan hệ to-one, không fetch collection trong pageable. |
| `TopicService.toResponse` | Lấy toàn bộ lịch sử review rồi `findFirst()` khi chỉ cần review mới nhất. | Trung bình | Truy vấn top 1 ở DB, giữ nội dung response. |

## Những phần đã đúng tại thời điểm bắt đầu

- Controller nhận request/validation/authorization và gọi Service; không thấy business workflow phức tạp hoặc Repository được inject vào Controller. Không cần di chuyển code hình thức.
- `DashboardService.currentSemester` đã dùng hai derived queries top 1, có fallback; không còn `findAll().stream().filter()` trong workspace hiện tại. Chỉ bổ sung kiểm thử hồi quy.
- `listComponents(periodId)` và `listRubrics(componentId)` đã lọc DB theo scope; Controller hiện yêu cầu scope. Giữ nguyên hợp đồng đang có, không đổi List thành PageResponse.
- Các `findAll(pageable)` / `findAll(specification, pageable)` đang phân trang đúng; không thay chỉ vì tên `findAll`.
- `RegistrationService.review` gọi bean `ProjectAssignmentService` với MANDATORY trong transaction chung. Self-invocation ở `ProjectAssignmentService.create` vẫn nằm trong transaction của `create`; không cần đổi propagation.
- `GroupService.create/addMember` và lời mời đã có khóa sinh viên. Ban đầu query membership được dự định là current read; kiểm thử MySQL sau đó xác nhận phép join vẫn có thể bỏ sót nhóm mới do chỉ khóa alias membership. Đây là lỗi đã sửa, không coi giải pháp cũ là đủ an toàn.
- Bảng khoa/bộ môn/học kỳ và cấu hình enum nhỏ: giữ API list hiện tại. Lọc vài người trong nhóm/hội đồng có giới hạn không phải lỗi tải toàn bảng.
- Exception nghiệp vụ đang dùng exception có sẵn; không thấy chuỗi return biểu diễn lỗi. Handler lỗi bất ngờ hiện không gửi stack trace cho frontend.
- Không tìm thấy TODO/FIXME trong Java production. Không có bằng chứng đủ để xóa dependency hoặc code nghi là bài lab; giữ nguyên.

## Giai đoạn 2 — Kết quả refactor

Các đường dẫn Java trong bảng dưới nằm trong `backend/src/main/java/com/example/qldetai/`; Repository liên quan được thay đổi cùng Service. Đã rà 28 Controller và 30 Service; giữ nguyên Controller/DTO hiện tại, không sửa frontend. Đã so sánh SHA-256 frontend với thời điểm bắt đầu: không file nào thay đổi bởi đợt rà soát này.

| File | Lỗi ban đầu | Thay đổi | Mức độ | Kết quả kiểm thử |
|---|---|---|---|---|
| `feature/groupregistration/service/GroupService.java`, `GroupInvitationService.java`, `repository/GroupMemberRepository.java` | Join membership chỉ sinh `FOR UPDATE OF membership`; MySQL có thể không thấy group vừa tạo trong bảng join. Đọc slot/count bằng snapshot có thể chọn trùng slot hoặc sai trạng thái. | Một native query nhỏ dùng `FOR UPDATE` cho cả hai bảng; current read thành viên sau khóa group; khóa group khi remove; current read đăng ký trước thay đổi thành viên. | Cao | Tạo nhóm cùng/các đợt khác nhau, thêm cùng sinh viên vào hai nhóm, tranh slot cuối, accept hai lời mời và remove cùng lúc đăng ký: đạt trên H2/MySQL. |
| `feature/groupregistration/service/RegistrationService.java` | Kiểm tra registration/project bằng snapshot sau khi chờ khóa; review/withdraw cạnh tranh. | Khóa và đọc hiện hành các aggregate cần kiểm tra; khóa registration khi review/withdraw; giữ kiểm tra version, quyền, cửa sổ đăng ký và trạng thái. | Cao | Đăng ký cùng nhóm, hai lần duyệt, tranh cùng đề tài, lý do từ chối sai, đăng ký lại sau REJECTED/WITHDRAWN: đạt. |
| `feature/project/service/ProjectAssignmentService.java`, `domain/ProjectAssignment.java` | Retry manual create có thể cùng insert; mapping chưa thể hiện unique group/topic đã có trong SQL. | Khóa registration ở entry manual, khóa group/topic, current read phân công; trả lại bản có sẵn; ánh xạ hai unique hiện có. Không thay MANDATORY. | Cao | Hai retry trả cùng ID; một topic chỉ một assignment; lỗi tạo assignment rollback quyết định duyệt; test lifecycle giữ group LOCKED/CLOSED: đạt. |
| `feature/evaluation/service/EvaluationService.java::assign` | Khóa project nhưng truy vấn count có thể thấy snapshot cũ; chưa kiểm trùng lecturer ở Service. | Lấy tập ACTIVE có khóa để kiểm capacity và lecturer. Không unique cả lịch sử CANCELLED. | Cao | Hai người tranh quota cuối và cùng người chấm khi còn quota: chỉ một request thành công. |
| `feature/evaluation/service/EvaluationService.java::createRubric`, `repository/RubricRepository.java`, `domain/Rubric.java` | `count + 1` trùng phiên bản; không chịu được khoảng trống; tiêu chí lồng nhau chưa được Bean Validation cascade. | Khóa component; current read phiên bản lớn nhất + 1; phản ánh unique SQL sẵn có; kiểm maxScore, tên/mô tả, sortOrder và sortOrder trùng ở Service. | Cao | Hai request cùng tạo cho ra version 1/2; max 7 tạo 8; tiêu chí âm/trùng bị từ chối và không lưu dở: đạt. |
| `feature/evaluation/service/EvaluationService.java::saveSheet` | Hai lần lưu đầu cùng version 0; thay detail không tăng version; delete/insert trùng unique khi lưu lại phiếu trên MySQL; điểm âm/null trong list chưa được cascade validation. | Khóa evaluation/sheet; revision phiếu mới bắt đầu 1; force increment nếu chỉ detail đổi; flush delete trước insert; kiểm điểm hợp lệ trước xóa. | Cao | Lưu nháp, sửa riêng điểm, stale version, hai lần lưu đầu, điểm âm, gửi điểm, hạn chấm và ownership: đạt. MySQL test đã tái hiện lỗi unique trước khi thêm flush. |
| `feature/evaluation/service/EvaluationCommitteeService.java`, `DefenseSessionService.java` | Hai thao tác add/schedule kiểm tra cùng dữ liệu cũ; thiếu khóa chung bảo vệ lịch overlap. | Khóa committee/project đúng thao tác; current read members/conflicts; bỏ query members lặp. | Cao | Hai CHAIR và hai lịch bảo vệ trùng giờ: chỉ một thành công; test reviewer/committee workflow hiện có đạt. |
| `feature/evaluation/service/EvaluationResultService.java` | Hai finalize có thể tạo cùng version hoặc nhiều bản FINALIZED; truy vấn children từng dòng danh sách. | Khóa project, current read result versions, max + 1; khóa source sheet; khóa result khi publish; batch inputs/members ở danh sách và fetch to-one. | Cao | Hai finalize chỉ một thành công; các test trung bình theo component, snapshot bất biến, chair và quyền đọc/công bố: đạt. |
| `feature/evaluation/service/GradeChangeService.java` | Hai review có thể ghi đè vì request không có version. | Khóa request khi review, giữ nguyên thứ tự flush approval rồi mở sheet. | Cao | Approve/reject đồng thời chỉ một quyết định; trạng thái sheet khớp quyết định; không mở snapshot đã finalize: đạt. |
| `feature/progress/service/ProgressService.java`, repository milestone/submission | Report dùng count + 1; khi milestone đã SUBMITTED thì status không đổi, @Version không đủ bảo vệ hai insert version. | Khóa milestone tại submit/update/feedback; current read phiên bản lớn nhất + 1. | Cao | Hai submission đồng thời đều thành công với version 1 và 2 trên H2/MySQL. |
| `feature/topic/service/TopicService.java` | Reassign supervisor có thể insert trước delete trên unique slot; thay child không tăng version topic. Lấy toàn bộ review chỉ để chọn một. | Khóa topic cho reassign, force increment version và flush delete; get dùng top 1; list batch supervisors/latest reviews, vẫn ẩn review theo quyền hiện tại. | Cao / Trung bình | Thay supervisor cũ không lỗi unique; version tăng và stale request bị từ chối; topic visibility/lifecycle HTTP tests: đạt. |
| Group/project/registration/evaluation/committee/defense/result/grade-change repositories và các phương thức list tương ứng | N+1 do lazy to-one hoặc query children/sheet/criteria trên từng dòng. | EntityGraph chỉ fetch to-one; lấy children theo tập ID của page/list rồi map; không collection fetch join vào Pageable. | Trung bình | Các test response/lifecycle/phân quyền đạt; đo query: rubric list 3 bản = 2 SQL; group page = 3 SQL; evaluation page = 3 SQL, gồm count khi cần. |
| `exception/GlobalExceptionHandler.java` | Concurrency và lỗi query parameter có thể thành 500. | Ánh xạ optimistic/pessimistic/timeout thành 409; binding/type/method-validation thành 400; giữ ApiResponse, không gửi diagnostic nội bộ. | Trung bình | 3 test MockMvc kiểm envelope, mã lỗi, lỗi thiếu/sai kiểu tham số và không có stack trace: đạt. |
| `feature/dashboard/service/DashboardService.java` (phần có sẵn) | Mục yêu cầu kiểm tra `findAll` đã được sửa trong workspace từ trước. | Giữ nguyên source; bổ sung 3 test current/fallback/không có semester, xác minh không gọi `findAll`. | Không sửa lại | 3 test đạt. |

Các phương thức private không còn caller sau refactor và vài import không dùng trong đúng file thay đổi đã được bỏ. Không xóa dependency, không format lại file ngoài phạm vi.

## Transaction và khóa

- Không đặt transaction ở Controller. Đọc vẫn `readOnly = true`; các thao tác nhiều bảng vẫn chung transaction ghi.
- `review -> createForApprovedRegistration` qua Spring bean giữ `Propagation.MANDATORY`. Exception khi tạo assignment không bị nuốt; registration, assignment, trạng thái nhóm và notification trong transaction được rollback cùng nhau.
- Với ghi vào child mà parent không đổi, `@Version` đơn thuần không tự tăng. Phiếu điểm và thay GVHD được tăng revision rõ ràng; API vẫn dùng chính trường version hiện tại.
- Khóa chỉ dùng ở các root hoặc tập child cần duy trì invariant: student/group, topic/registration/project, component/rubric, committee/schedule, evaluation/sheet và milestone/submission. Không thêm khóa vào API danh sách.
- Native membership query là ngoại lệ có bằng chứng từ SQL thực tế MySQL 8.4: JPQL trước đó chỉ khóa alias membership, không khóa bảng group trong join; test cùng sinh viên tạo nhóm ở hai đợt chứng minh yêu cầu khóa cả hai.
- Hai approval cạnh tranh có thể gây deadlock do cập nhật registrations còn lại của topic. MySQL rollback request thua; handler trả 409 để tải lại và thử lại. Không tự retry thao tác nghiệp vụ có side effect. Test xác nhận trạng thái cuối một APPROVED/một REJECTED và một assignment.

## Giai đoạn 3 — Verification

- Đã chạy thực tế `backend/mvnw.cmd -B clean compile test`: **BUILD SUCCESS; 98 tests, 0 failures, 0 errors, 0 skipped** (21/10/2026). Lệnh clean ép compile lại toàn bộ 225 source files và 19 test source files.
- Kết quả từng suite được lưu trong `backend-audit-test-results.json` cùng thư mục. Đây là snapshot kết quả full suite H2 trước lượt MySQL bổ sung.
- Bộ test mới gồm `BackendConsistencyIntegrationTests` (28 test, có hai thread/connection và commit riêng), `GlobalExceptionHandlerTests` (3), `DashboardServiceTests` (3).
- Đã chạy MySQL 8.4.10 trong container tạm, chỉ bind `127.0.0.1` qua cổng ngẫu nhiên, data trên tmpfs; nạp toàn bộ schema thật: 41 bảng, 54 unique, 104 check, 113 foreign key, 27 trigger. Isolation `REPEATABLE-READ`; không dùng database ứng dụng đang chạy.
- Lượt MySQL cuối chạy `mvnw.cmd -B -Daudit.mysql.url=jdbc:mysql://127.0.0.1:<cổng-container>/ql_de_tai_sinh_vien?allowPublicKeyRetrieval=true -Dtest=BackendConsistencyIntegrationTests,GlobalExceptionHandlerTests,DashboardServiceTests test`: **BUILD SUCCESS; 34 tests, 0 failures, 0 errors, 0 skipped**. Trong đó 28 integration test dùng MySQL, 6 test còn lại là unit/MockMvc. Container tạm đã được dừng sau kiểm thử.
- Các lượt test trước đã phát hiện lỗi thực tế và được sửa: duplicate score detail trên MySQL; version phiếu đầu tiên; membership join dùng snapshot. Không dùng kết quả chạy lỗi trước khi sửa để tuyên bố thành công.
- `git diff --check` không có lỗi whitespace; SHA-256 xác nhận frontend, DTO và Controller giữ nguyên so với đầu đợt rà soát. Không chạy frontend E2E vì không sửa frontend; compatibility HTTP được kiểm bởi test backend hiện có.

## Migration và dữ liệu cũ

**Không cần migration cho database đúng schema hiện tại.** Các unique được thêm vào annotation chỉ phản ánh `uq_rubrics_01` và `uq_project_assignments_02/03` đã tồn tại trong SQL. Runtime vẫn `spring.jpa.hibernate.ddl-auto=none`; không tạo/đổi/xóa bảng, cột hoặc dữ liệu đang có. Không chạy bootstrap trên database hiện hữu.

Đã cung cấp `database/checks/backend-audit-preflight.sql`, chỉ chứa SELECT, để người quản trị kiểm tra index và dữ liệu cũ trước khi đưa lên môi trường khác. Script không tự xóa hoặc sửa record trùng. Nếu database triển khai thực tế khác schema (thiếu unique/index hoặc đã có dữ liệu sai), cần kiểm tra từng deployment và lập migration riêng sau khi đối chiếu; chưa có bằng chứng để tự thêm constraint mới ở đây.

## Cảnh báo giữ nguyên và giới hạn

- Danh sách components/rubrics đã có scope bắt buộc và trả `List`; không đổi thành PageResponse, cắt bớt dữ liệu hoặc bắt frontend xử lý pagination mới. Nếu số phiên bản rubric tăng rất lớn, cần thống nhất cơ chế phân trang API riêng; hiện đã batch criteria.
- Các response chứa lịch sử submissions/feedback hoặc danh sách riêng của sinh viên vẫn giữ toàn bộ dữ liệu như hợp đồng cũ. Chưa đo tải dữ liệu thật để kết luận cần thay API; không âm thầm truncate response.
- Catalog nhỏ (khoa/bộ môn/học kỳ), lọc role/nhóm/hội đồng có giới hạn và `findAll(pageable)` không phải lỗi cần refactor.
- Propagation outbox/audit và xử lý SMTP đã tách bean; giữ nguyên. SMTP vẫn có đặc tính giao nhận ít nhất một lần khi mất kết nối sau khi máy chủ đã nhận thư; không coi unit/integration test là bằng chứng exactly-once.
- Chưa kiểm tra database production, dữ liệu production, tải nhiều instance, stress test dài hạn hoặc hiệu năng trên dữ liệu lớn thực tế. Các test đồng thời xác minh những interleaving cụ thể, không phải chứng minh mọi lịch thực thi.
- Không có benchmark p95/p99 hoặc EXPLAIN trên dữ liệu production. Số query đã đo áp dụng cho các test fixture và đường đọc nêu trên.
- Có warning Mockito tự attach agent trên JDK 21; test vẫn chạy thành công. Không đổi dependency/JVM flag chỉ để ẩn warning.
- Toàn bộ thay đổi có sẵn trước khi bắt đầu, gồm frontend, email template, README và các Controller scope, được giữ nguyên. Không commit hoặc push lên `main`.
