# Backend 2 — dàn ý thuyết trình và script ngắn

Tài liệu này để bạn dùng khi:

- Thuyết trình với giảng viên.
- Giải thích phần backend2 với bạn trong nhóm.
- Demo nhanh dự án.

Mục tiêu là nói ngắn, rõ, đúng trọng tâm nghiệp vụ và kỹ thuật.

## 1. Phiên bản nói ngắn 30 giây

“Phần backend2 của em xử lý toàn bộ vòng đời từ đề tài đến lúc giao đề tài cho nhóm sinh viên. Cụ thể, em chia thành 3 module: quản lý đề tài, quản lý nhóm và đăng ký đề tài, cuối cùng là giao đề tài chính thức. Em dựng theo kiến trúc feature-based giống backend1 để dễ ghép dự án, có kiểm tra quyền, rule nghiệp vụ, audit, notification và optimistic locking cho các thao tác cập nhật.”

## 2. Phiên bản nói 1 phút

“Backend2 của em giải quyết 3 bài toán chính. Thứ nhất là module `topic`, cho phép giảng viên hoặc sinh viên đề xuất đề tài, sau đó giảng viên hoặc admin duyệt, gán giảng viên hướng dẫn và publish đề tài. Thứ hai là module `groupregistration`, cho phép sinh viên tạo nhóm, thêm hoặc xóa thành viên, rồi trưởng nhóm đăng ký đề tài. Thứ ba là module `project`, dùng để chốt một đăng ký đã được duyệt thành quyết định giao đề tài chính thức cho nhóm.

Về mặt kỹ thuật, em tổ chức code theo package nghiệp vụ, mỗi module có entity, repository, DTO, service và controller riêng. Rule nghiệp vụ nằm ở service để controller mỏng. Em tái sử dụng hạ tầng sẵn có của backend1 như session auth, CSRF, `NotificationService`, `BusinessAudit`, format `ApiResponse` và exception handler.”

## 3. Cách trình bày 3 đến 5 phút

### Mở bài

“Phần backend2 em làm tập trung vào quá trình một đề tài đi từ lúc được đề xuất đến lúc được giao cho một nhóm sinh viên.”

### Nói về module 1: `topic`

Bạn có thể nói:

“Module đầu tiên là quản lý đề tài. Ở đây em có entity `Topic` để lưu thông tin chính của đề tài, `TopicReview` để lưu lịch sử duyệt, và `TopicSupervisor` để lưu danh sách giảng viên hướng dẫn. Trong `TopicService`, em xử lý các rule như chỉ tạo đề tài trong thời gian proposal, mã đề tài không trùng trong cùng đợt, chỉ topic đã duyệt mới được publish, và muốn publish thì phải có giảng viên hướng dẫn chính.”

Ý chính cần nhấn:

- Có vòng đời rõ ràng cho topic.
- Có phân biệt topic `OPEN` và `RESERVED`.
- Có optimistic locking bằng `version`.

### Nói về module 2: `groupregistration`

Bạn có thể nói:

“Module thứ hai là quản lý nhóm và đăng ký đề tài. Em tách thành `StudentGroup`, `GroupMember` và `TopicRegistration`. `GroupService` xử lý việc tạo nhóm, thêm hoặc xóa thành viên. `RegistrationService` xử lý việc nhóm đăng ký đề tài, duyệt đăng ký hoặc rút đăng ký. Ở đây em giữ các rule như mỗi sinh viên chỉ ở một nhóm active trong cùng đợt, nhóm tối đa 3 thành viên, chỉ trưởng nhóm mới được thao tác, và nhóm không được đổi thành viên nếu đã vào giai đoạn đăng ký hoặc đã được giao đề tài.”

Ý chính cần nhấn:

- Tách nhóm và đăng ký thành 2 service khác nhau.
- Có soft delete thành viên bằng `leftAt`.
- Có rule leader-only rất rõ.

### Nói về module 3: `project`

Bạn có thể nói:

“Module cuối là giao đề tài. Khi một đăng ký đã được duyệt, giảng viên hoặc admin có thể tạo `ProjectAssignment`. Lúc đó nhóm được khóa lại, và các đăng ký pending khác của cùng đề tài sẽ bị reject tự động. Sau này assignment có thể chuyển sang `COMPLETED` hoặc `CANCELLED`. Nếu hoàn thành thì nhóm đóng, còn nếu hủy thì nhóm mở lại.”

Ý chính cần nhấn:

- Assignment là bước chốt cuối cùng.
- Có transition status rõ ràng.
- Có tác động dây chuyền tới trạng thái nhóm và đăng ký khác.

### Kết bài

“Điểm em ưu tiên là giữ logic nghiệp vụ tập trung ở service, tái sử dụng hạ tầng backend1 để ghép dễ, và có test cho các flow quan trọng để tránh lỗi khi tích hợp.”

## 4. Dàn ý slide gợi ý

Nếu bạn làm slide, có thể đi theo 6 slide:

### Slide 1: Mục tiêu backend2

- Quản lý đề tài.
- Quản lý nhóm sinh viên.
- Quản lý đăng ký đề tài.
- Giao đề tài chính thức.

### Slide 2: Kiến trúc

- Chia theo feature package.
- Mỗi feature có entity, repository, DTO, service, controller.
- Tái sử dụng auth, audit, notification của backend1.

### Slide 3: Flow nghiệp vụ

```text
Đề xuất đề tài
-> review
-> gán giảng viên hướng dẫn
-> publish
-> tạo nhóm
-> đăng ký đề tài
-> duyệt đăng ký
-> giao đề tài
```

### Slide 4: Rule nghiệp vụ chính

- Topic chỉ publish sau khi approved.
- Topic publish phải có supervisor chính.
- Mỗi sinh viên chỉ ở một nhóm active trong một đợt.
- Nhóm tối đa 3 thành viên.
- Chỉ leader mới được quản lý nhóm và đăng ký.
- Chỉ topic published mới được đăng ký.
- Assignment chỉ tạo từ registration approved.

### Slide 5: Điểm kỹ thuật

- `@PreAuthorize` để kiểm tra role.
- `BusinessAudit` để lưu log nghiệp vụ.
- `NotificationService` để gửi thông báo.
- `version` để chống ghi đè song song.
- Integration test cho các flow quan trọng.

### Slide 6: Kết quả

- Ghép được với cấu trúc backend1.
- Build và test pass.
- Có tài liệu API và tài liệu giải thích từng file.

## 5. Script demo trực tiếp

Nếu bạn demo live, có thể nói và thao tác theo thứ tự này:

1. “Đầu tiên em đăng nhập bằng tài khoản có quyền phù hợp.”
2. “Em tạo một đề tài mới ở trạng thái chờ duyệt.”
3. “Sau đó em review đề tài và chuyển sang approved.”
4. “Em gán giảng viên hướng dẫn chính và phụ.”
5. “Khi đã đủ điều kiện, em publish đề tài.”
6. “Bây giờ em chuyển sang tài khoản sinh viên, tạo nhóm và thêm thành viên.”
7. “Leader của nhóm đăng ký đề tài vừa publish.”
8. “Giảng viên/admin duyệt đơn đăng ký đó.”
9. “Cuối cùng em tạo assignment để chính thức giao đề tài cho nhóm.”
10. “Khi assignment được tạo, nhóm bị khóa và các đăng ký pending khác của cùng đề tài sẽ bị loại.”

## 6. Câu hỏi giảng viên có thể hỏi và cách trả lời ngắn

### “Vì sao em tách thành 3 package?”

Trả lời gợi ý:

“Vì 3 nhóm nghiệp vụ này có liên quan nhưng không trùng trách nhiệm. Tách theo feature giúp đọc code dễ hơn, ghép với backend1 dễ hơn, và sau này mở rộng từng phần mà không làm rối package.”

### “Vì sao không để logic trong controller?”

Trả lời gợi ý:

“Em để logic ở service để controller mỏng, dễ test, dễ tái sử dụng và giữ đúng layering.”

### “Vì sao dùng `version`?”

Trả lời gợi ý:

“Để chống trường hợp hai người cùng sửa một dữ liệu. Nếu dữ liệu đã đổi, backend trả conflict để người dùng reload lại.”

### “Vì sao nhóm bị khóa sau khi giao đề tài?”

Trả lời gợi ý:

“Vì sau khi giao đề tài chính thức thì thành phần nhóm phải ổn định, tránh việc đổi người làm thay đổi quyết định đã duyệt.”

### “Vì sao assignment lại reject các đăng ký pending khác của cùng topic?”

Trả lời gợi ý:

“Vì một đề tài chỉ được giao chính thức cho một nhóm. Khi đã có assignment thì các đăng ký chờ khác của cùng topic không còn hợp lệ nữa.”

### “Em đã test những gì?”

Trả lời gợi ý:

“Em có test flow vòng đời đề tài, flow nhóm và slot thành viên, flow đăng ký đề tài, và flow assignment gồm cả việc reject các đăng ký pending khác và đổi trạng thái nhóm.”

## 7. Điểm mạnh nên tự nhấn khi báo cáo

- Dựng lại backend2 theo đúng kiến trúc hiện tại của project thay vì code lệch cấu trúc.
- Có rule nghiệp vụ rõ ràng trong service.
- Tái sử dụng được backend1 thay vì viết lại hạ tầng.
- Có tài liệu chi tiết từng file và tài liệu API.
- Có test cho luồng chính.

## 8. Một đoạn kết ngắn để chốt bài

“Phần backend2 em không chỉ dừng ở việc tạo endpoint, mà em cố gắng hoàn thiện cả vòng đời nghiệp vụ, kiểm tra quyền, ràng buộc dữ liệu, trạng thái, tài liệu hóa và test. Mục tiêu là khi ghép chung với backend1 và các phần khác thì hệ thống vẫn nhất quán và dễ bảo trì.”
