# Cấu trúc dự án

```text
QuanLyDeTaiSinhVien/
├── backend/                 # Spring Boot API, chia theo feature
├── database/
│   ├── migrations/          # Các migration MySQL có version
│   ├── seed/                # Dữ liệu và thao tác dành cho môi trường demo
│   └── ql_de_tai_sinh_vien_schema.sql
├── docs/                    # Hợp đồng API, hướng dẫn và tài liệu dự án
├── docker/                  # Bootstrap container
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
- Dữ liệu demo chỉ được nạp khi MySQL khởi tạo volume mới; mọi tệp seed phải
  ghi rõ phạm vi local và cách reset dữ liệu trong phần đầu tệp hoặc README.
