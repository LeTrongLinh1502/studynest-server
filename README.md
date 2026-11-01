# StudyNest Server

Spring Boot 4, Java 21, MySQL. Các migration V2–V6 nằm trong `src/main/resources/db/migration`; không tạo khóa ngoại. V2/V3 cung cấp môn học, chủ đề, lớp học, bài học và tài liệu. V4/V5 cung cấp câu hỏi, đề trắc nghiệm, lượt làm, chấm điểm, thống kê, báo cáo nội dung và nhật ký. V6 lưu câu hỏi được học sinh đánh dấu để xem lại trong lượt làm.

Đặt `DB_USERNAME`, `DB_PASSWORD` trong môi trường chạy IntelliJ. Tùy chọn `STUDYNEST_UPLOAD_DIR` chỉ thư mục lưu file bài học. Chạy `mvnw.cmd spring-boot:run` trên Windows.

Luồng cơ bản: Teacher tạo câu hỏi → ghép đề → xuất bản → giao cho lớp. Student mở đề → bắt đầu lượt làm → chọn đáp án (lưu ngay trên server) → nộp bài → xem kết quả nếu chính sách cho phép. Admin xử lý báo cáo trong `/api/admin/content-reports`.

Teacher có thể tạo tối đa 20 câu hỏi cùng chủ đề bằng `POST /api/teacher/questions/batch` với body `{ "topicId": 1, "questions": [{ "difficulty": "MEDIUM", "stem": "...", "explanation": "...", "options": [{ "content": "...", "isCorrect": true }, { "content": "...", "isCorrect": false }] }] }`. Toàn bộ đợt tạo là một transaction; API tạo/sửa một câu vẫn hoạt động.

Giao diện mới dùng `GET /api/dashboard` để tải số liệu theo role, `GET /api/lesson-resources/{id}/preview` để xem PDF có kiểm tra quyền, và `PUT /api/attempts/{id}/flags/{questionId}` để đánh dấu câu hỏi. Khi khởi động BE, Flyway sẽ áp dụng V6 nếu chưa có trong `flyway_schema_history`.
