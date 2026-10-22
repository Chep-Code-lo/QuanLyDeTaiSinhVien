# Audit cuối — 22/10/2026

## Phạm vi và nguồn sự thật

Kiểm tra code trên nhánh `main`, bắt đầu tại `7f6186c`, đang có 7 commit local từ công việc trước. Không dùng kết luận audit cũ làm bằng chứng thay cho code. Dự án thực tế dùng Spring Boot 4 và Java 21; không đổi phiên bản hay kiến trúc theo mô tả vai trò trong yêu cầu.

Không commit, push, deploy, reset database, xóa Docker volume hoặc sửa dữ liệu hiện có. Hai image Docker được build với tag riêng, không thay container ứng dụng đang chạy. Chỉ ghi dữ liệu fixture trong H2 và container MySQL 8.4 kiểm thử riêng dùng tmpfs. Database Docker local hiện có chỉ được đọc bằng transaction READ ONLY để xuất báo cáo.

## 1. Lỗi được xác nhận và cách sửa

| Mã | Bằng chứng trong code hiện tại | Khắc phục |
| --- | --- | --- |
| F01 | Truy vấn trùng phòng chỉ dùng `lower(trim(location))`; `Phòng A101` và `A101` khác nhau | Thêm khóa so sánh bỏ tiền tố Phòng/Phong và khoảng trắng, không phân biệt hoa thường. Query tính cùng khóa cho nhãn cũ ngay tại database. Không đổi nhãn lưu, không migration. Kiểm thử cả Unicode whitespace và hai request tranh chấp |
| F02 | DTO và frontend cho địa điểm 255 ký tự, nhưng schema MySQL chỉ cho 200 | Giới hạn DTO và input về 200, trả lỗi validation thay vì lỗi ghi SQL. Test biên 200/201 ký tự |
| F03 | Query lịch sinh viên không fetch các quan hệ mà mapper đọc | Dùng EntityGraph giống query lịch quản trị, lấy project/topic/group/period/committee cùng lần đọc. Test xóa persistence context rồi xác nhận đúng 1 SQL statement |
| F04 | Autocomplete gọi `allPages()` chỉ để tìm một lecturer ID | Thêm filter `lecturerId` vào API danh mục đang có; truy vấn theo PK, vẫn áp dụng ACTIVE/khoa/bộ môn. Frontend chỉ yêu cầu trang 0, size 1. Không thêm API quản trị hoặc mở quyền mới |
| F05 | Quản lý học thuật tải mọi trang đợt để hiển thị một bảng | Dùng API phân trang hiện có, 10 dòng/trang; chặn response cũ sau đổi trang/unmount. Thêm error/retry, không hiển thị lỗi mạng như danh sách trống. Sau tạo thành công, tải lại trang đầu |
| F06 | Trang duyệt đăng ký tải mọi hồ sơ và toàn bộ đề tài published dù giảng viên không dùng danh mục đó | Phân trang hồ sơ của luồng quản lý, 10 dòng/trang; bỏ request đề tài/nhóm/đợt chỉ dùng cho sinh viên. Giữ dữ liệu và kiểm tra đăng ký của sinh viên, không đổi quyền backend. Thêm error/retry và bỏ response cũ |
| F07 | Hộp xác nhận lịch chỉ có mã đề tài; component không tự lọc project theo hội đồng khi nhận nhiều đợt | Bổ sung tên đề tài, nhóm, hội đồng, phòng, thời gian; lọc cùng đợt và xóa project đã chọn khi đổi hội đồng. Backend vẫn là nơi kiểm tra cuối cùng |
| F08 | Record `UserPrincipal` mặc định đưa trường `password` chứa hash vào `toString()` | Override `toString()` chỉ giữ ID, trạng thái và role; test không lộ hash/email/họ tên, không thay `getPassword()` hay xác thực. Không có bằng chứng hash đã bị ghi vào log production |
| F09 | Ba input quản lý thông báo chỉ có placeholder; nút đóng biểu mẫu đợt thiếu accessible name | Bổ sung nhãn hỗ trợ đọc màn hình và tên nút đóng. Giữ bố cục và nghiệp vụ hiện tại |

**Lựa chọn bổ sung của người dùng:** giữ BCrypt cho mật khẩu mới (cost 12), không dùng PBKDF2 để mở rộng độ dài. Vẫn đọc hash BCrypt cũ và hash PBKDF2 nếu đã có từ bản sửa trước, không chuyển đổi dữ liệu. Validation ký tự 8–100 theo yêu cầu vẫn giữ nguyên. Test xác nhận BCrypt 72 byte hoạt động, vượt giới hạn bị từ chối thay vì cắt ngầm, và các hash cũ vẫn đăng nhập được.

Các file: xem [danh sách đầy đủ](final-audit-2026-10-22-files.md).

## 2. Những vấn đề đã được xử lý từ trước, không sửa lại

- **Đổi ngày đợt đã xếp lịch:** `RegistrationPeriodService.update()` chỉ cho sửa DRAFT. ACTIVE/CLOSED/CANCELLED không được sửa; bảng chuyển trạng thái không cho quay về DRAFT. Test bổ sung tạo lịch KLTN thực tế, thử đổi ngày rồi xác nhận bị từ chối và ngày cũ giữ nguyên. Không thêm cơ chế tự chuyển lịch hoặc thêm khóa trùng lặp. Các dòng DRAFT bất thường từ dữ liệu cũ vẫn cần quản trị viên xem xét, không được coi là luồng được hỗ trợ.
- **Ngày KLTN:** backend so sánh cả bắt đầu/kết thúc bằng LocalDate Việt Nam; chặn trước/sau ngày và qua nửa đêm. Frontend đã nhận `defenseDate`, mặc định giờ trong ngày đó, kiểm tra trước khi gọi API và chuyển giờ Việt Nam sang UTC. Không áp quy tắc này cho loại khác.
- **Hạn phản biện:** cấu hình KLTN đã chặn ngày Việt Nam của `reviewDeadlineAt` sau `defenseDate`. Cùng ngày được phép vì ngày bảo vệ không chứa giờ. Hạn chót không đồng nghĩa phải chờ tới hạn: khi xếp lịch, tất cả phân công phản biện thực tế phải có phiếu SUBMITTED/LOCKED và đủ chi tiết rubric. Không tự siết thành “phải trước một ngày” hoặc đổi ngày đã nhập. Chưa có quy tắc mới về ghi nhận hồi tố phiên trong quá khứ.
- **Xung đột:** singleton `defense_schedule_guard` được khóa trước project/hội đồng và giữ tới commit; truy vấn xung đột dùng locking/current read. Không đổi cơ chế khóa. Chỉ SCHEDULED giữ tài nguyên; hai phiên nối tiếp nhau được phép. Unique assignment vẫn áp dụng cả phiên đã hủy/hoàn tất, vì chưa có nghiệp vụ bảo vệ lại.
- **Hội đồng:** đã kiểm tra giới hạn theo đợt, đúng một Chủ tịch/Thư ký, không trùng người và không đưa GVHD vào hội đồng đề tài đó. Không khôi phục quy tắc số người chấm tĩnh đã được bỏ trước đây.
- **Lưu một phần:** lưu cấu hình hội đồng và phân công là thao tác tách biệt; batch phân công có transaction, rollback và retry không tạo trùng. Test hiện hữu vẫn chạy.
- **Giao diện lịch:** giữ biểu mẫu khi API lỗi; lỗi conflict hiển thị tiếng Việt khi lưu; success chỉ sau API thành công; lỗi refresh sau lưu có thông báo riêng và nút tải lại. Đã có trạng thái phiên tiếng Việt và lý do chưa đủ điều kiện.
- **Topics:** đã phân trang/tìm kiếm/lọc tại backend. **Groups:** luồng quản lý đã phân trang và chỉ tải lời mời gửi đi của nhóm mà user làm trưởng nhóm. **Dashboard:** đã giữ dữ liệu khi cập nhật nền lỗi và tránh request focus/timer trùng.
- Các controller lịch/phân công có `@PreAuthorize` kiểm tra project/hội đồng, danh sách dùng phạm vi đọc trước pagination. Các route sinh viên, CSRF, tài khoản khóa và session được kiểm thử bởi các suite hiện hữu. Không tìm thấy bằng chứng cần thay mô hình Role/Academic Appointment trong phạm vi đã kiểm tra.

## 3. Kết quả dữ liệu lịch sử — database Docker local

Đã chạy [script chỉ đọc](../database/audits/defense-consistency-readonly.sql), không chạy migration hoặc lệnh sửa dữ liệu. [Bản xuất TSV](final-audit-2026-10-22-data.tsv) chứa ID, loại vi phạm và mốc thời gian, không chứa thông tin đăng nhập hoặc danh tính sinh viên.

| Loại | Bản ghi | Kết quả |
| --- | --- | --- |
| Phiên sai ngày KLTN | Phiên 1, đợt 3, COMPLETED | Ngày cấu hình 03/04/2026; phiên rơi vào 04/04/2026 giờ Việt Nam |
| Phiên sai ngày KLTN | Phiên 2, đợt 8, SCHEDULED | Ngày cấu hình 01/05/2027; phiên bắt đầu 14/10/2026, kết thúc 23/10/2026 giờ Việt Nam |
| Hạn phản biện sau ngày bảo vệ | Đợt 6 | Hạn 05/03/2027; bảo vệ 01/03/2027 |
| Hạn phản biện sau ngày bảo vệ | Đợt 8 | Hạn 05/05/2027; bảo vệ 01/05/2027 |

Snapshot có **3 phiên, 2 SCHEDULED, 1 dòng khóa singleton**. Không có dòng báo trùng phòng/giảng viên/hội đồng, trùng project hoặc roster không hợp lệ trong snapshot này. Điều đó không xác nhận dữ liệu trên VPS.

Script liệt kê thêm interval sai, roster thiếu/trùng, sai đợt giữa phiên/project/hội đồng, và từng phiên thuộc project bị trùng. Roster DRAFT chưa sử dụng được phân loại thành thông tin cần hoàn tất, không đánh đồng với dữ liệu hỏng. Đã chạy script trên fixture MySQL và xác nhận phát hiện bốn nhóm lỗi cố ý tạo riêng trong database kiểm thử: sai ngày, trùng phòng, trùng hội đồng, trùng giảng viên. Xem [kết quả kiểm tra script](final-audit-2026-10-22-sql-check.json).

**Không sửa 4 bản ghi vi phạm.** Phiên đã hoàn tất có thể liên quan điểm/kết quả công bố. Quản trị viên cần xác minh dữ liệu đúng và chọn quy trình sửa có kiểm soát; không dùng script này để sửa hàng loạt.

## 4. Kiểm thử và build

| Kiểm tra | Kết quả |
| --- | --- |
| `mvnw.cmd -q clean verify` | 222 test, 0 failure/error/skipped; đóng gói backend thành công |
| MySQL 8.4.10, `BackendConsistencyIntegrationTests` | 44 test, 0 failure/error/skipped; schema bootstrap thật, constraint/trigger thật, transaction trên các connection riêng |
| TypeScript `npm run typecheck` | Đạt |
| Oxlint `npm run lint` | Exit 0, còn 5 warning có sẵn; không tắt rule |
| Vitest `npm test` | 124 test trong 14 file, tất cả đạt |
| `npm run test:utils` | 20/20 đạt |
| `npm run build` | Production build đạt |
| Playwright + Edge | 78/78 lượt smoke test, 390/768/1440 px, các vai trò ADMIN/LECTURER/DEAN/DEPARTMENT_HEAD/STUDENT và khách |
| Docker backend | Build image `qldetai-final-audit-backend:20261022` thành công |
| Docker frontend | Build image `qldetai-final-audit-frontend:20261022` thành công |
| `git diff --check` | Đạt |

MySQL test tranh chấp lịch yêu cầu đúng một thành công và request còn lại là `ConflictException`; không coi deadlock hoặc lỗi integrity là kết quả hợp lệ cho những tình huống lịch này. Suite còn kiểm tra xung đột phòng cùng/khác cách viết, giảng viên chung, cùng hội đồng, phiên nối tiếp, duplicate project, thời gian sai, roster, rollback batch và phạm vi đọc theo appointment. Không thay locking vì chưa tìm thấy lỗi trong các ca lịch đã chạy.

Lượt MySQL kiểm tra code lịch cuối cùng được chạy trước khi nhận lựa chọn giữ BCrypt. Sau thay đổi encoder, toàn bộ suite H2/unit và Docker backend được chạy lại; không thay đổi thêm truy vấn hoặc transaction lịch. Không coi lượt MySQL đó là kiểm thử xác thực BCrypt trên MySQL.

Các suite H2 bao gồm Spring Security/CSRF, đăng nhập/session, file upload, password, email authorization và workflow. Test ngày KLTN trước/sau/qua nửa đêm có sẵn vẫn được giữ. Test frontend cũ khôi phục lecturer và tạo đợt được cập nhật mock theo hợp đồng truy vấn ID/phân trang mới; giữ các assertion chức năng, không xóa hoặc giảm yêu cầu để làm xanh.

Trong quá trình sửa, một lượt Vitest thất bại vì mock tạo đợt không đưa bản ghi vừa tạo vào kết quả GET được tải lại. Đã sửa fixture mô phỏng dữ liệu server, giữ assertion ngày hiển thị và chạy lại toàn bộ suite thành công. Một lệnh Maven/MySQL ban đầu chưa khởi chạy test do batch Windows tách ký tự `&` trong JDBC URL; đã dùng URL hợp lệ và thực sự chạy các lượt MySQL nêu trên. Không tính hai lượt này là kiểm thử thành công.

Chi tiết: [H2/unit](final-audit-2026-10-22-h2.json), [MySQL](final-audit-2026-10-22-mysql.json), [browser](final-audit-2026-10-22-browser.json). Browser dùng API fixture, kiểm tra render/route/overflow/lỗi JavaScript; không phải E2E với dữ liệu thật và không chứng minh backend phân quyền chỉ bằng việc ẩn menu. Không có test cuối bị skip hoặc bị chặn do môi trường.

## 5. Chưa xử lý và giới hạn xác minh

**Giới hạn mật khẩu được giữ theo lựa chọn người dùng:** BCrypt chỉ nhận tối đa **72 byte UTF-8**, khác với tối đa 100 ký tự của DTO/frontend. Mật khẩu ASCII trên 72 ký tự hoặc Unicode ít ký tự hơn nhưng vượt 72 byte vẫn có thể qua validation rồi bị từ chối lúc encode. Handler IllegalArgumentException hiện có trả HTTP 400. Không thêm quy tắc byte mới, không đổi thuật toán ghi và không tuyên bố hỗ trợ trọn vẹn 100 ký tự khi lưu.

1. **Dữ liệu lịch sử:** giữ nguyên theo yêu cầu; 4 bản ghi vi phạm cần xử lý nghiệp vụ riêng, đặc biệt phiên COMPLETED.
2. **Sửa/hủy/xếp lại lịch:** chưa có API đủ nghiệp vụ liên quan phân công chấm, phiếu khóa và kết quả công bố. Không thêm nút hoặc endpoint có thể làm dữ liệu không nhất quán.
3. **Các `allPages()` còn lại:** thông báo, danh sách chọn phân công/chấm điểm, milestone và danh mục đợt/giảng viên trong form tạo đề tài vẫn có nơi tải nhiều trang. Luồng sinh viên còn tải lịch sử đăng ký và đề tài cho lựa chọn, lời mời PENDING theo user. Chưa tuyên bố tối ưu toàn bộ những nơi này. Chỉ cắt còn một trang sẽ làm mất lựa chọn, kiểm tra đăng ký trùng hoặc trạng thái chưa đọc. Cần chuyển đồng bộ tìm kiếm/dropdown sang API thích hợp; riêng thông báo đang ghép unread bằng title/content trên client, nên cần thay hợp đồng dữ liệu trước khi phân trang để giữ đúng hành vi.
4. **N+1:** sửa đường đọc lịch sinh viên có bằng chứng; các kiểm tra query-count tiến độ/kết quả hiện hữu vẫn đạt. Chưa đo mọi API bằng dữ liệu lớn hay profiling VPS; không bổ sung index dựa trên suy đoán. Khóa phòng tính từ biểu thức chưa có index riêng; cần EXPLAIN/đo tải khi lịch lớn trước khi chọn cột khóa hoặc danh mục phòng.
5. **Proxy/IP:** Nginx đang append X-Forwarded-For, backend dùng forwarded headers. Tính tin cậy của chuỗi header phụ thuộc đường vào Cloudflare/reverse proxy và việc chặn truy cập trực tiếp backend. Chưa xác minh topology/ACL trên VPS; không khẳng định chống giả mạo IP chỉ từ test local, và không tự đổi cấu hình có thể làm sai IP qua tunnel hiện tại.
6. **Lint:** 2 warning Fast Refresh tại AuthContext/ToastContext; 1 cảnh báo chartOffset ở Dashboard; 2 cảnh báo Date.now trong hàm xử lý Groups/EvaluatorAssignment. Không phát hiện lỗi chạy tương ứng qua các test đã chạy; không đổi các phần đang đúng chỉ để bỏ cảnh báo. Không có cảnh báo set-state-in-effect/exhaustive-deps mới.
7. **Bảo mật và UI:** đã đọc controller/service liên quan và chạy suite hiện hữu; đây không phải pentest độc lập mọi endpoint/role. Browser smoke dùng dữ liệu rỗng/fixture, chưa kiểm chứng tất cả modal nhiều dữ liệu, thiết bị thật, screen reader hoặc SMTP thật.

## 6. Kiểm thử thủ công cần thực hiện

- Đối chiếu 2 phiên và 2 đợt trong TSV với hồ sơ gốc; quyết định cách xử lý lịch sử và kết quả đã công bố, không tự đổi ngày cho “khớp”.
- Với từng vai trò, mở trực tiếp URL và gọi API bằng ID ngoài phạm vi khoa/bộ môn; kiểm tra thêm trên cấu hình VPS thực tế, không chỉ menu.
- Xếp lịch KLTN gần 00:00/23:59 giờ Việt Nam, cùng ngày hạn phản biện; kiểm tra phiếu phản biện hoàn tất và không cho vượt ngày. Đối chiếu ngày hiển thị giữa quản trị và sinh viên, thông báo gửi tới thành viên nhóm.
- Hai người đồng thời xếp cùng phòng với các nhãn tương đương; xác nhận một thành công, một thông báo conflict rõ ràng, form thất bại còn dữ liệu.
- Kiểm tra phòng có cùng mã ở hai cơ sở, tên hội trường hoặc tên gọi tắt chưa tương đương về chuỗi; giữ thông tin cơ sở trong tên phòng để phân biệt.
- Tạo đợt khi đang ở trang sau, đổi trạng thái, thử lỗi mạng rồi retry; kiểm tra phân trang hồ sơ và lý do từ chối ở phía sinh viên.
- Dùng bàn phím trên các form/modal có nhiều dữ liệu và thử màn hình điện thoại thật. Kiểm tra SMTP/attachment thực tế trong môi trường kiểm thử được phép.
- Xác minh ACL/trusted proxy, HTTPS cookie và nguồn client IP trên VPS trước khi coi thông tin IP là tin cậy cho audit/rate limit.

Không cập nhật stack đang chạy. Các thay đổi vẫn nằm trong working tree để xem xét; không tạo commit mới.
