# Quản lý đề tài sinh viên

Ứng dụng web hỗ trợ nhà trường quản lý vòng đời đề tài sinh viên, từ đề xuất,
xét duyệt và đăng ký đến hướng dẫn, nộp báo cáo, phản biện, bảo vệ và công bố
kết quả. Hệ thống tập trung dữ liệu học thuật, phân công giảng viên và theo dõi
tiến độ của từng nhóm sinh viên trên cùng một nền tảng.

## Chức năng chính

- Quản lý khoa, bộ môn, học kỳ và các đợt đăng ký đề tài.
- Đề xuất, xét duyệt, công bố đề tài và phân công giảng viên hướng dẫn.
- Tạo nhóm sinh viên, mời thành viên và đăng ký đề tài.
- Quản lý mốc tiến độ, nộp báo cáo, tệp đính kèm và phản hồi của giảng viên.
- Thiết lập thành phần đánh giá và bộ tiêu chí chấm điểm.
- Phân công phản biện độc lập, thành lập hội đồng và xếp lịch bảo vệ.
- Chấm điểm, tổng hợp, chốt và công bố kết quả; xử lý yêu cầu điều chỉnh điểm.
- Quản lý thông báo, cấu hình email, tài khoản, bổ nhiệm và nhật ký thao tác.

## Người dùng và phân quyền

Nhóm sinh viên mặc định có từ **3 đến 5 thành viên** theo rubric. Nhóm mới tạo
có thể dưới 3 người để tiếp tục mời thành viên, nhưng chỉ được đăng ký và nhận
giao đề tài khi đủ số thành viên theo cấu hình đợt (trong khoảng 3–5).
Migration `V010__rubric_group_size.sql` cập nhật cấu hình đợt cũ ngoài giới hạn
về 3–5 và tính lại trạng thái nhóm đang thành lập/hoạt động; không xóa thành viên
hay thay đổi các đề tài đã giao. Nhóm cũ thiếu/thừa người cần được điều chỉnh
trước lần đăng ký tiếp theo. Chạy migration qua dịch vụ `db-migrate` khi triển khai.

| Đối tượng | Phạm vi sử dụng |
|---|---|
| Quản trị viên | Quản trị tài khoản, bổ nhiệm, cấu hình hệ thống và giám sát thông tin học thuật. |
| Trưởng khoa | Quản lý nghiệp vụ học thuật trong khoa theo bổ nhiệm còn hiệu lực. |
| Trưởng bộ môn | Quản lý nghiệp vụ được phép trong bộ môn theo bổ nhiệm còn hiệu lực. |
| Giảng viên | Đề xuất đề tài, hướng dẫn, phản hồi báo cáo và chấm điểm theo phân công. |
| Sinh viên | Quản lý nhóm, đăng ký đề tài, nộp báo cáo và xem lịch/kết quả được phép. |

Role trong source gồm `ADMIN`, `LECTURER`, `STUDENT`. Trưởng khoa (`DEAN`)
và Trưởng bộ môn (`DEPARTMENT_HEAD`) là chức vụ bổ nhiệm của giảng viên.
Admin không mặc định kế thừa quyền nghiệp vụ của giảng viên hoặc Trưởng khoa.

## Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Frontend | React 19, TypeScript, Vite, React Router, CSS |
| Backend | Java 21, Spring Boot 4.1.1, Spring Security, Spring Data JPA |
| Database | MySQL 8.4, Flyway migration |
| Triển khai | Docker Compose, Nginx reverse proxy |
| Kiểm thử | JUnit, Spring Boot Test, H2, Vitest, Testing Library |

## Cấu trúc thư mục

```text
QuanLyDeTaiSinhVien/
├── backend/                 # REST API, phân quyền và nghiệp vụ
├── frontend/                # Giao diện React và cấu hình Nginx
├── database/
│   ├── bootstrap/           # Khởi tạo database
│   ├── schema/              # Bảng và ràng buộc cục bộ
│   ├── constraints/         # Khóa ngoại
│   ├── seed/                # Dữ liệu nền hệ thống
│   ├── objects/             # View và trigger
│   └── migrations/          # Các bản nâng cấp database
├── scripts/                 # Tiện ích phát triển cục bộ
├── .env.example             # Mẫu cấu hình Docker Compose
└── docker-compose.yml
```

## Khởi chạy bằng Docker

### 1. Chuẩn bị

Cài Docker Desktop trên Windows hoặc Docker Engine kèm Compose v2 trên Linux.
Chạy các lệnh bên dưới tại thư mục gốc dự án. Không cần cài riêng Java, Node.js
hoặc MySQL nếu chạy toàn bộ hệ thống bằng Docker.

### 2. Tạo cấu hình môi trường

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Linux/macOS:

```sh
cp .env.example .env
```

Mở `.env`, thay hai mật khẩu MySQL bằng giá trị riêng. Để chạy HTTP trên máy
cá nhân, chỉnh các biến sau; mẫu `.env.example` hiện hướng đến môi trường HTTPS:

```dotenv
MYSQL_APP_USER=ql_de_tai_app
MYSQL_APP_PASSWORD=your-local-database-password
MYSQL_ROOT_PASSWORD=your-different-local-root-password
APP_BASE_URL=http://localhost:5173
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
SESSION_COOKIE_SECURE=false
CSRF_COOKIE_SECURE=false
EMAIL_ENABLED=false
```

Email có thể tắt trong quá trình chạy thử. Khi bật, cấu hình các biến `MAIL_*`
và đặt `EMAIL_ENABLED=true`. File `.env` chứa thông tin riêng, được loại khỏi Git.

### 3. Build và khởi động

```sh
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

Compose khởi động MySQL, nạp schema/dữ liệu nền nếu volume mới, chạy migration,
chuẩn bị thư mục lưu file, sau đó chạy backend và frontend.

| Dịch vụ | Địa chỉ mặc định |
|---|---|
| Giao diện | http://localhost:5173 |
| Kiểm tra backend | http://localhost:8080/api/v1/health |
| MySQL từ máy chủ cục bộ | `127.0.0.1:3307` |
| Tên database | `ql_de_tai_sinh_vien` |

Frontend chuyển tiếp `/api/v1` đến backend qua Nginx. Backend và MySQL chỉ
được publish trên địa chỉ loopback của máy chạy Docker.

### 4. Xem log, dừng và cập nhật

```sh
# Xem log ứng dụng
docker compose logs -f backend frontend

# Dừng các dịch vụ, giữ container và dữ liệu
docker compose stop

# Khởi động lại sau khi cập nhật mã nguồn
docker compose up -d --build
```

Dữ liệu MySQL và file upload nằm trong các volume `mysql_data`, `backend_files`.
Không dùng `docker compose down -v` nếu cần giữ dữ liệu. Với database đã tồn
tại, Flyway áp dụng migration còn thiếu; bootstrap không được chạy lại.

Database mới chỉ có role và khóa giao dịch nền, không có tài khoản hay đề tài
mẫu. Cần cấp phát tài khoản quản trị ban đầu theo quy trình triển khai hoặc
khôi phục database đã có tài khoản để đăng nhập.

## Chạy backend/frontend riêng khi phát triển

Yêu cầu thêm **JDK 21**, **Node.js 24 và npm** (phiên bản Node dùng trong CI).
Dự án có Maven Wrapper nên không bắt buộc cài Maven riêng. Chuẩn bị `.env`
ở thư mục gốc theo phần chạy cục bộ bên trên.

Nếu đang chạy toàn bộ ứng dụng bằng Docker, dừng hai dịch vụ ứng dụng để giải
phóng cổng trước khi chạy riêng:

```sh
docker compose stop backend frontend
```

### 1. Khởi động database và migration

Tại thư mục gốc:

```sh
docker compose up -d mysql
docker compose run --rm db-migrate
```

### 2. Chạy backend

Windows PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```sh
cd backend
bash ./mvnw spring-boot:run
```

Backend mặc định dùng MySQL tại `localhost:3307` và đọc `.env` ở thư mục gốc.
Nếu dùng database khác, đặt `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. File upload
khi chạy riêng mặc định nằm trong thư mục tạm của hệ điều hành; dùng
`FILE_STORAGE_ROOT` để chỉ định thư mục lưu lâu dài.

### 3. Chạy frontend

Mở terminal khác:

```sh
cd frontend
npm ci
npm run dev
```

Truy cập `http://localhost:5173`. Vite proxy `/api` đến backend theo cấu hình
trong `vite.config.ts`; nếu tạo `frontend/.env`, giữ `VITE_API_BASE_URL=/api/v1`
để sử dụng proxy này.


## Chạy trên máy chủ

1. Chuyển mã nguồn lên máy chủ và tạo `.env` từ `.env.example`.
2. Đặt mật khẩu MySQL riêng, `APP_BASE_URL` và `CORS_ALLOWED_ORIGINS` theo tên miền.
3. Cấu hình reverse proxy/TLS cho HTTPS; đặt `SESSION_COOKIE_SECURE=true` và
   `CSRF_COOKIE_SECURE=true`. Nếu proxy chạy trên cùng máy, có thể đặt
   `FRONTEND_BIND_ADDRESS=127.0.0.1` để chỉ mở frontend qua proxy.
4. Chạy `docker compose config --quiet`, sau đó `docker compose up -d --build`.

Nếu chuyển một hệ thống đã có dữ liệu, chuyển riêng database và volume file
upload. Mã nguồn không chứa các dữ liệu này. Các file demo đã được loại bỏ;
giữ nguyên thư mục `database/` để bootstrap và migration hoạt động.
