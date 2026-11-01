# StudyNest BE — V2 + V3

## Chạy local

Backend dùng Java 21, MySQL `studynest_db`, Maven wrapper và session cookie. Cần đặt `DB_USERNAME`, `DB_PASSWORD` trong môi trường chạy IntelliJ hoặc terminal. Có thể đặt `STUDYNEST_UPLOAD_DIR` để lưu file ngoài source; mặc định là `./data/uploads` và thư mục này đã được bỏ qua bởi Git. Chạy `./mvnw.cmd spring-boot:run` trên Windows.

V2/V3 Flyway dùng `CREATE TABLE IF NOT EXISTS` vì các bảng này có thể đã được tạo thủ công trong DataGrip. Bảng phải có đúng các cột trong migration. Không có `FOREIGN KEY`; service kiểm tra ID, quyền sở hữu và trạng thái trước khi ghi.

Đăng ký công khai tạo `STUDENT`. Admin đầu tiên được cấp role thủ công trong DB. Sau khi Admin duyệt đơn Teacher, người được duyệt cần đăng xuất rồi đăng nhập lại để session nhận quyền mới.

## API

Tất cả endpoint dưới đây nằm trên `http://localhost:8080`. Request thay đổi dữ liệu cần gửi cookie session và CSRF header `X-XSRF-TOKEN` lấy từ `GET /api/auth/csrf`.

| Role | Endpoint | Ý nghĩa |
| --- | --- | --- |
| Public | `GET /api/subjects`, `GET /api/subjects/{id}/topics` | Danh mục đang hoạt động |
| Student | `POST /api/teacher-applications`, `GET /api/teacher-applications/me` | Nộp/xem đơn Teacher |
| Admin | `GET /api/admin/teacher-applications?status=PENDING`, `PATCH /api/admin/teacher-applications/{id}` | Xem và duyệt đơn |
| Admin | `GET/POST /api/admin/subjects`, `PUT/DELETE /api/admin/subjects/{id}` | Quản lý môn, DELETE chuyển ARCHIVED |
| Admin | `GET /api/admin/subjects/{id}/topics`, `POST /api/admin/topics`, `PUT/DELETE /api/admin/topics/{id}` | Quản lý chủ đề |
| Teacher | `GET/POST /api/teacher/classes`, `GET/PUT/DELETE /api/teacher/classes/{id}` | Quản lý lớp của mình |
| Teacher/Admin | `GET /api/teacher/classes/{id}/members` | Thành viên lớp; Teacher phải sở hữu lớp |
| Teacher | `DELETE /api/teacher/classes/{id}/members/{studentId}` | Xóa thành viên khỏi lớp |
| Student | `POST /api/classes/join`, `GET /api/classes/my` | Tham gia/xem lớp |
| Teacher | `GET/POST /api/teacher/lessons`, `GET/PUT/DELETE /api/teacher/lessons/{id}`, `POST /api/teacher/lessons/{id}/publish` | Soạn/xuất bản/ẩn bài |
| Teacher | `POST /api/teacher/lessons/{id}/resources/links`, `POST /api/teacher/lessons/{id}/resources/files`, `PATCH/DELETE /api/teacher/lessons/{id}/resources/{resourceId}` | Link, file, sắp xếp và xóa tài liệu |
| Teacher | `GET/POST /api/teacher/classes/{id}/assignments`, `DELETE /api/teacher/classes/{id}/assignments/{assignmentId}` | Giao/hủy bài học |
| Student | `GET /api/lessons?topicId=1`, `GET /api/lessons/{id}`, `POST /api/lessons/{id}/complete`, `GET /api/classes/my/assignments` | Xem bài, tự đánh dấu hoàn thành, lịch giao bài |
| Người có quyền xem bài | `GET /api/lesson-resources/{id}/download` | Tải file |

### Payload chính

```json
POST /api/admin/subjects
{"code":"MATH","name":"Toán"}

POST /api/admin/topics
{"subjectId":1,"parentTopicId":null,"code":"ALGEBRA","name":"Đại số","sortOrder":0}

POST /api/teacher-applications
{"reason":"Tôi muốn tạo tài liệu Toán"}

PATCH /api/admin/teacher-applications/1
{"decision":"APPROVED","reviewNote":"Đã xác minh"}

POST /api/teacher/classes
{"name":"Toán 10A","description":"Học kỳ 1"}

POST /api/classes/join
{"joinCode":"ABCDEFGH"}

POST /api/teacher/lessons
{"topicId":1,"title":"Hàm số bậc nhất","summary":"Kiến thức cơ bản","body":"Nội dung bài học","visibility":"CLASS"}

POST /api/teacher/lessons/1/resources/links
{"resourceType":"VIDEO_LINK","title":"Video tham khảo","externalUrl":"https://example.com/video"}

POST /api/teacher/classes/1/assignments
{"lessonId":1,"availableAt":"2026-11-01T00:00:00Z","dueAt":"2026-11-15T00:00:00Z"}
```

Upload file dùng `multipart/form-data` với phần `title` và `file`. Chấp nhận PDF, DOCX, PPTX tối đa 20 MB. File nằm ngoài Git; API chỉ trả metadata và ID tài liệu. `body` là nội dung do Teacher nhập; FE cần hiển thị như văn bản/Markdown an toàn, không chèn thẳng HTML chưa làm sạch.

Bài `PUBLIC` đã xuất bản được Student xem. Bài `CLASS` chỉ mở khi Student là thành viên đang hoạt động của lớp đã được giao bài và `availableAt` đã tới. `dueAt` là hạn tham khảo, không khóa quyền đọc bài. Endpoint `complete` chỉ ghi nhận Student tự đánh dấu đã học.
