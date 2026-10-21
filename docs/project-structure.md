# Cấu trúc dự án

```text
QuanLyDeTaiSinhVien/
├── backend/                 # Spring Boot API, chia theo feature
│   └── src/main/java/com/example/qldetai/
│       ├── common/audit/    # Audit nghiệp vụ và ghi audit log
│       ├── common/observability/ # Logging cuộc gọi service chậm
│       ├── common/persistence/ # Base entity và callback timestamp JPA
│       └── feature/         # Controller, service, domain, DTO theo nghiệp vụ
├── database/
│   ├── migrations/          # Chỉ nâng cấp database cũ, giữ dữ liệu hiện có
│   ├── checks/              # SQL kiểm tra dữ liệu, không khởi tạo database
│   ├── seed/demo-data.sql   # Dữ liệu demo tùy chọn, không nạp trên VPS
│   └── ql_de_tai_sinh_vien_schema.sql # File SQL chính: cấu trúc và vai trò hệ thống
├── docker-compose.yml        # Khởi chạy các dịch vụ bằng Docker Compose
├── docker-compose.demo.yml   # Override chủ động dùng khi cần demo local
├── docs/                    # Hợp đồng API, hướng dẫn và tài liệu dự án
├── frontend/
│   ├── public/              # Tài nguyên tĩnh
│   ├── src/
│   │   ├── api/             # HTTP client theo tài nguyên
│   │   ├── app/             # Ghép provider và route gốc
│   │   ├── components/      # Thành phần dùng lại giữa nhiều màn hình
│   │   ├── config/          # Cấu hình điều hướng và phạm vi dữ liệu
│   │   ├── context/         # Auth và toast provider
│   │   ├── features/        # UI theo từng nghiệp vụ độc lập
│   │   ├── layouts/         # Khung ứng dụng
│   │   ├── pages/           # Các trang gắn với route
│   │   ├── routes/          # Khai báo route
│   │   ├── styles/          # CSS global và CSS dùng chung
│   │   ├── types/           # Kiểu dữ liệu dùng chung
│   │   └── utils/           # Hàm thuần, không phụ thuộc UI
│   └── tests/               # Kiểm thử frontend
└── scripts/                 # Script hỗ trợ phát triển
```

Quy ước khi thêm mã mới:

- Mã chỉ dùng cho một nghiệp vụ đặt trong `frontend/src/features/<feature>/`.
- Component hoặc utility dùng từ hai màn hình trở lên đặt trong `components/`
  hoặc `utils/`.
- CSS của một trang/component nằm cạnh tệp TypeScript; CSS global đặt trong
  `styles/`.
- Dữ liệu demo chỉ tự nạp khi chủ động chọn override demo và MySQL khởi tạo volume mới; mọi tệp seed phải
  ghi rõ phạm vi local và cách reset dữ liệu trong phần đầu tệp hoặc README.
- Cross-cutting concern đặt trong `common/` theo trách nhiệm; không tạo lại
  thư mục `aspect/` chung chung.
