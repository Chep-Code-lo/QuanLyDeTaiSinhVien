# Kiểm tra dữ liệu lịch bảo vệ

`defense-consistency-readonly.sql` chạy trên MySQL 8.4, chỉ đọc trong một transaction có snapshot nhất quán. File không phải migration, không sửa lịch hay cấu hình và không cần nạp bảng múi giờ MySQL. Timestamp trong database được hiểu là UTC; ngày Việt Nam được tính bằng UTC+07:00.

Dùng tài khoản chỉ có quyền SELECT trên database cần kiểm tra. Ví dụ trên Windows, sau khi đã cấu hình login path bằng công cụ MySQL của môi trường:

```powershell
cmd /c "mysql --login-path=qldetai-audit --database=ql_de_tai_sinh_vien --batch --default-character-set=utf8mb4 < database\audits\defense-consistency-readonly.sql > defense-audit.tsv"
```

Không đặt mật khẩu trong lệnh hoặc commit thông tin đăng nhập. Chạy từ thư mục gốc repository. `record_id` là ID phiên, trừ các dòng kiểm tra hội đồng hoặc đợt, khi đó là ID hội đồng hoặc đợt tương ứng. `related_id` là phiên còn lại trong cặp xung đột; với đề tài có nhiều phiên, đó là ID project assignment. Các dòng cuối là tổng số phiên và số dòng khóa singleton, giúp phân biệt không có vi phạm với database không có dữ liệu.

| Mã | Ý nghĩa |
| --- | --- |
| GRADUATION_DATE | Phiên KLTN thiếu ngày cấu hình hoặc ngày bắt đầu/kết thúc không khớp ngày Việt Nam |
| INVALID_INTERVAL | Kết thúc không sau bắt đầu |
| ROOM_OVERLAP | Hai phiên SCHEDULED có cùng khóa phòng và thời gian chồng lấn |
| COMMITTEE_OVERLAP | Một hội đồng có hai phiên SCHEDULED chồng lấn |
| LECTURER_OVERLAP | Một giảng viên có hai phiên SCHEDULED chồng lấn |
| DUPLICATE_PROJECT_SESSION | Liệt kê từng phiên của project có nhiều phiên, bao gồm trạng thái đã hủy/hoàn tất, theo unique hiện tại |
| INVALID_COMMITTEE_ROSTER | Hội đồng đã sử dụng hoặc xác nhận không đủ số lượng, thiếu/trùng chức danh hoặc trùng người |
| DRAFT_ROSTER_NOT_READY | Bản nháp chưa được sử dụng, chưa đủ thành viên; là thông tin cần hoàn tất, không phải dữ liệu hỏng |
| REVIEW_DEADLINE_AFTER_DEFENSE_DAY | Đợt KLTN có hạn phản biện sau ngày bảo vệ theo giờ Việt Nam |
| PERIOD_MISMATCH | Phiên, project và hội đồng không cùng đợt |

Phòng được so sánh bằng cách đổi chữ thường, bỏ tiền tố `Phòng`/`Phong` và bỏ khoảng trắng. `Phòng A101`, `phòng a101`, `A101`, `Phòng A 101` cùng khóa `a101`. Dấu gạch nối, tên cơ sở, tầng hoặc tòa nhà vẫn được giữ; các cách gọi khác về ngữ nghĩa cần quản trị viên xác minh. Code và script chỉ tính khóa khi so sánh, không đổi nhãn đã lưu. Chưa cần bảng danh mục phòng hoặc migration; bổ sung danh mục sau này cần ánh xạ dữ liệu cũ có kiểm duyệt.

Các phiên đã hủy/hoàn tất vẫn được kiểm tra ngày lịch sử; chỉ SCHEDULED giữ phòng, hội đồng và giảng viên. Khoảng thời gian là `[bắt đầu, kết thúc)`, nên hai phiên nối tiếp nhau được phép. Một dòng LECTURER_OVERLAP cho mỗi giảng viên chung là có chủ đích.

Kết quả chỉ là bằng chứng để rà soát. Không tự sửa ngày đợt, chuyển lịch, xóa phiên hoặc sửa kết quả đã công bố. Bản xuất ngày 22/10/2026 trong `docs/final-audit-2026-10-22-data.tsv` thuộc database Docker local đã được kiểm tra, không phải xác nhận dữ liệu trên VPS.
