# Kết quả sửa 7 vấn đề backend

Ngày 20/10/2026. Chỉ thay đổi backend, schema và tài liệu; không thay đổi frontend.

| Vấn đề | Thay đổi |
| --- | --- |
| 1. Hạn nộp điểm | Phân công phải có hạn tương lai; hạn REVIEW không vượt hạn phản biện của đợt. Chặn lưu nháp/gửi phiếu sau hạn phân công hoặc hạn phản biện, và chặn phân công CANCELLED. |
| 2. SV trùng nhóm khác đợt | Tạo nhóm, thêm thành viên và chấp nhận lời mời đều kiểm tra nhóm FORMING/ACTIVE/LOCKED trên mọi đợt. Nhóm CLOSED hoặc thành viên đã rời nhóm không chặn đợt khác. Đóng/hủy đợt không giải phóng SV còn trong nhóm đang thực hiện. Khóa dòng SV và đọc khóa membership để tránh hai yêu cầu đồng thời vượt kiểm tra trên MySQL REPEATABLE READ. |
| 3. Quyền tổng hợp | Đề tài có hội đồng chỉ CHAIR của đúng hội đồng được finalize, kể cả ADMIN không được thay chủ tịch. Đề tài không có hội đồng giữ quyền ADMIN/người quản lý đúng phạm vi. Quyền công bố vẫn theo luồng quản lý hiện có. |
| 4. Giới hạn hội đồng | Siết 3–5 ở DTO, kiểm tra cấu hình đợt, service phân công hội đồng, xếp lịch và schema. Slot thành viên tối đa 5. Các ràng buộc nằm trong file schema duy nhất. |
| 5. Thiếu thành phần khi tổng kết | Duyệt toàn bộ thành phần cấu hình trong đợt; tất cả phải LOCKED, có đủ evaluator ACTIVE và đủ phiếu đã nộp/khóa theo expectedEvaluators. Không bỏ qua thành phần chưa gán ai. |
| 6. Quyền đọc kết quả | Dùng chung predicate cho danh sách và chi tiết. ADMIN được đọc toàn bộ; GV chỉ đọc khi quản lý đúng khoa/bộ môn còn hiệu lực, được phân công chấm ACTIVE hoặc thuộc hội đồng không hủy của đề tài. GVHD chỉ đọc kết quả PUBLISHED của đề tài hướng dẫn. Lọc trước phân trang để không lộ tổng số bản ghi ngoài phạm vi. SV vẫn chỉ xem kết quả PUBLISHED có mình trong snapshot. |
| 7. Đăng ký khi đợt đóng/hủy | Yêu cầu đợt ACTIVE ngoài các kiểm tra thời gian, nhóm, trưởng nhóm, trạng thái đề tài và đăng ký đã có. |

## Kiểm chứng

- Bộ test đầy đủ: **50 tests, 0 failures, 0 errors, 0 skipped**, gồm 9 test mới cho các quy tắc trên.
- Sau khi siết thêm trường hợp đợt cũ CLOSED/CANCELLED và chủ tịch hội đồng khác, chạy lại **8/8 test liên quan đều đạt**.
- Kiểm thử HTTP kiểm tra người thường/thư ký/admin không được finalize đề tài có hội đồng và chủ tịch đúng hội đồng được phép.
- Kiểm thử quyền đọc kiểm tra danh sách, tổng số bản ghi, chi tiết, chức vụ sai khoa/hết hiệu lực và SV trước/sau công bố.
- Kiểm thử nhóm kiểm tra tạo/thêm khác đợt, chấp nhận lời mời sau khi đã tham gia nhóm khác, và cho phép tham gia sau khi nhóm cũ CLOSED.
- Các test chạy H2; Docker daemon chưa chạy nên chưa xác minh schema/trigger/khóa đồng thời trên MySQL thật.

## File SQL duy nhất

Sử dụng [ql_de_tai_sinh_vien_schema.sql](../database/ql_de_tai_sinh_vien_schema.sql) để khởi tạo database. File này đã có đầy đủ ràng buộc hội đồng 3–5 người và slot tối đa 5; không cần file migration riêng.

Database đã khởi tạo trước đó không tự cập nhật khi chỉ sửa file schema. Chưa chạy lại SQL trên database của người dùng trong lượt này.

## Các file chính

- [EvaluationService](../backend/src/main/java/com/example/qldetai/feature/evaluation/service/EvaluationService.java): hạn chấm.
- [EvaluationResultService](../backend/src/main/java/com/example/qldetai/feature/evaluation/service/EvaluationResultService.java): quyền finalize và thành phần bắt buộc.
- [ResultAccess](../backend/src/main/java/com/example/qldetai/feature/evaluation/service/ResultAccess.java): phạm vi đọc kết quả.
- [GroupMemberRepository](../backend/src/main/java/com/example/qldetai/feature/groupregistration/repository/GroupMemberRepository.java): truy vấn membership khác đợt có khóa.
- [RegistrationService](../backend/src/main/java/com/example/qldetai/feature/groupregistration/service/RegistrationService.java): kiểm tra đợt ACTIVE.
- [Hợp đồng API cập nhật](backend-3-api.md).
