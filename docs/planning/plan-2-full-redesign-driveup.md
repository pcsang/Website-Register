# Plan 2 — Đổi code đầy đủ theo design DriveUp (Backend + Frontend)

**Đây là quyết định nghiệp vụ, không chỉ kỹ thuật.** Plan này mô tả việc thật sự đưa domain "trường dạy
lái xe" (khoá học, học viên theo hạng bằng, lịch khai giảng, KPI doanh thu/tỷ lệ đậu) vào hệ thống — nghĩa
là sản phẩm hiện tại ("Information Collection & Admin Management System" chung chung) sẽ chuyển thành một
nền tảng đăng ký học lái xe cụ thể. Khuyến nghị: **chỉ bắt đầu plan này sau khi xác nhận đây thật sự là
hướng sản phẩm muốn theo** — nếu chỉ thích giao diện, dừng ở Plan 1 là đủ và an toàn hơn nhiều.

Design gốc (`driveup-claude-cli-prompt-design-UI-UX.md`) tự mô tả là **frontend-only với mock data**
("Chỉ làm phần frontend UI với mock data... Chưa gọi API thật"). Plan này đi xa hơn yêu cầu gốc đó theo
đúng yêu cầu của bạn: làm thật cả backend, không dừng ở mock.

## Quyết định cần chốt trước khi code (Decisions Needed)

| # | Vấn đề | Khuyến nghị |
|---|---|---|
| 1 | Đổi tên entity `Submission` → `Student`? | **Giữ tên `Submission`**, chỉ mở rộng field — đổi tên là việc cosmetic, tốn công rename xuyên suốt backend/frontend/test mà không có lợi ích chức năng; có thể đổi tên hiển thị (label UI) mà không đổi tên entity/bảng. |
| 2 | Trạng thái 3 mức hiện tại (NEW/IN_PROGRESS/COMPLETED) → 4 mức mới (Chờ tư vấn/Đã xác nhận/Đang học/Đã tốt nghiệp)? | Đây là **breaking change** dữ liệu thật đang có (nếu đã có submission thật trong DB production). Cần chiến lược map: `NEW→PENDING_CONSULTATION`, `IN_PROGRESS→CONFIRMED` hoặc giữ `IN_PROGRESS`, `COMPLETED→GRADUATED`, kèm migration dữ liệu, không chỉ đổi enum trong code. |
| 3 | Chỉ số doanh thu ("2,16 tỷ") | Không có khái niệm thanh toán/payment trong hệ thống hiện tại. **Khuyến nghị v1**: tính gần đúng = tổng giá `Course` × số học viên đã "Đã xác nhận"/"Đang học" trở lên cho khoá đó — là số ước lượng, không phải doanh thu thật đã thu. Cần nói rõ trong UI đây là ước tính, hoặc bỏ hẳn card này nếu không chấp nhận số liệu xấp xỉ. |
| 4 | Tỷ lệ đậu thi ("98,2%") | Không có khái niệm "kết quả thi" ở đâu trong hệ thống/design. **Khuyến nghị v1**: không xây dựng cả 1 domain "Exam" mới cho 1 con số — dùng 1 giá trị admin tự nhập tay (field cấu hình đơn giản, ví dụ 1 bảng `dashboard_settings` key-value hoặc field trên 1 config entity), không tính toán từ dữ liệu giao dịch thật. |
| 5 | Khái niệm "Chi nhánh" (branch) | Design nhắc "12 Chi nhánh toàn quốc" và filter "chi nhánh" ở trang Courses. **Khuyến nghị v1**: thêm 1 field string đơn giản trên `Course` (không tạo hẳn entity `Branch` riêng) — đủ dùng cho filter/hiển thị, tránh over-engineer khi chưa rõ nhu cầu quản lý chi nhánh sâu hơn (địa chỉ, quản lý riêng...). |
| 6 | "Giáo viên & Xe" (mục sidebar bị mock đánh dấu "coming soon") | **Không làm** ở Plan 2 — giữ đúng tinh thần mock: disabled, không route thật. `Course.teacherName` chỉ là string đơn giản, không có entity `Teacher` riêng. |
| 7 | Route `/` hiện đang redirect sang `/form` | Landing page mới (8 section) sẽ **thay thế** `/form` làm trang chủ thật (`/`) — `/form` cũ (1 form đơn) coi như được gộp vào section "Đăng ký" (mục 7 trong file design) của landing page mới, không giữ song song 2 trang đăng ký. |

Nếu có ý kiến khác với khuyến nghị nào ở trên, nên chốt trước khi bắt đầu — đổi giữa chừng sẽ ảnh hưởng
migration đã chạy.

## Đề xuất chia nhỏ thành các phase (giống cách project đang làm theo roadmap)

Không làm 1 lần — rủi ro cao, khó review, khó rollback. Đề xuất phụ lục cho
`java-spring-boot-angular-project-prompts.md` (hoặc coi là initiative riêng), theo thứ tự:

### Phase D1 — Backend: domain `Course`
- Entity `Course`: `id, name, licenseClass (enum B1/B2/C), price, durationMonths, practiceHours,
  description, branch (string), teacherName (string), seatsTotal, startDate, createdAt, updatedAt`.
  `seatsRegistered` **không** lưu trực tiếp trên `Course` — tính bằng `COUNT` học viên có `courseId` này
  và status ≥ CONFIRMED, tránh 2 nguồn sự thật (giống cách `DashboardService` hiện tại đang tính toán
  bằng COUNT query thay vì lưu số đếm sẵn).
- `CourseStatus` derive (không lưu DB): Còn chỗ (< 70% chỗ) / Sắp đầy (70–99%) / Đã đầy (100%) — tính ở
  tầng mapper/service, giống style `SubmissionMapper` hiện có.
- Flyway migration mới `V2__add_courses_table.sql` (không sửa `V1`).
- `CourseRepository`, `CourseService`, `CourseController` — theo đúng layering Controller→Service→
  Repository, DTO record, constructor injection... (các convention đã ghi trong `CLAUDE.md`).
- Endpoint: `GET /api/courses` (public, đọc — cho landing page hiển thị giá/khoá học),
  `GET/POST/PATCH /api/admin/courses` (ROLE_ADMIN, giống pattern `AdminSubmissionController`).

### Phase D2 — Backend: mở rộng `Submission` + trạng thái mới
- Migration `V3__extend_submissions.sql`: thêm cột `course_id` (FK tới `courses`, nullable — học viên có
  thể đăng ký tư vấn trước khi chọn khoá cụ thể), đổi `status` sang 4 giá trị mới kèm data migration theo
  quyết định #2 ở trên (viết migration SQL update dữ liệu cũ, không chỉ đổi enum Java).
- `SubmissionStatus` enum: `PENDING_CONSULTATION, CONFIRMED, IN_PROGRESS, GRADUATED` (đặt tên tiếng Anh
  rõ nghĩa, hiển thị tiếng Việt ở tầng frontend, giống cách hiện tại `NEW/IN_PROGRESS/COMPLETED` hiển thị
  nguyên bản không dịch — cân nhắc có dịch hiển thị hay không, dùng chung 1 cách với hệ thống hiện tại để
  nhất quán).
- Mở rộng `CreateSubmissionRequest` thêm `courseId` (optional hoặc required tuỳ có bắt buộc chọn khoá
  ngay lúc đăng ký hay không — theo mock, form đăng ký có field "Hạng bằng muốn học" dạng select, map
  sang chọn `licenseClass` rồi backend tự gợi ý/gán `courseId` phù hợp, hoặc đơn giản hoá: field select
  gửi thẳng `courseId`).
- `AdminSubmissionController`'s list endpoint thêm filter `courseId`.

### Phase D3 — Backend: Dashboard overview mở rộng
- Query mới: đăng ký theo tháng (6 tháng gần nhất) — `GROUP BY` theo tháng trên `created_at`, trả mảng
  `[{month, count}]`.
- Lịch khai giảng sắp tới: `Course` có `startDate` trong tương lai gần nhất, kèm số chỗ đã đăng ký/tổng.
- Doanh thu ước tính + tỷ lệ đậu theo quyết định #3/#4 ở trên.
- `GET /api/admin/dashboard/overview` (endpoint mới, tách khỏi `/summary` hiện có để không phá API cũ
  đang được frontend hiện tại dùng — hoặc thay thế hẳn `/summary` nếu Plan 1 cũng bị bỏ song song; khuyến
  nghị **thêm mới, không xoá `/summary`** cho tới khi chắc chắn không còn nơi nào gọi).

### Phase D4 — Frontend: `AdminLayoutComponent` thật (dùng lại từ Plan 1 nếu đã làm)
Nếu Plan 1 đã làm sidebar/topbar, tái sử dụng nguyên, chỉ thêm mục menu **Khoá học & Lịch học** →
`/admin/courses` (không còn "coming soon" vì backend Course đã có thật).

### Phase D5 — Frontend: trang Overview + Students + Courses (dữ liệu thật, không mock)
- `overview`: 4 KPI card nối `GET /api/admin/dashboard/overview`; chart "Đăng ký theo tháng" vẽ bằng
  div/flexbox thuần theo %, **không thêm chart lib mới** (project hiện không có `ngx-charts`/`Chart.js`,
  và design doc cũng cho phép cách này khi chưa có sẵn lib) — nhất quán với nguyên tắc "không thêm
  dependency không cần thiết" của `CLAUDE.md`.
- `students` (đổi tên hiển thị từ "Submissions", giữ route `/admin/dashboard` hoặc tách route
  `/admin/students` tuỳ quyết định UX — nếu tách, cần cập nhật `authGuard` áp dụng route mới): filter
  thêm theo `courseId`, badge 4 trạng thái mới.
- `courses`: bảng khoá học (progress bar chỗ ngồi, badge trạng thái), dải lịch tuần (component mới, thuần
  hiển thị — không cần logic phức tạp), toolbar filter hạng bằng/trạng thái/chi nhánh.

### Phase D6 — Frontend: Landing page thật (8 section)
- `LandingPageComponent` tại `/`, nối `GET /api/courses` (thật, không mock) cho section "Khoá học" (giá,
  mô tả lấy từ DB thay vì hard-code 3 gói B1/B2/C như mock — nếu muốn giữ đúng 3 gói cố định như mock thì
  đây chính là 3 row đầu tiên seed trong `V2__add_courses_table.sql` hoặc 1 migration seed riêng).
- Form đăng ký (section 7) POST thẳng `CreateSubmissionRequest` mở rộng (Phase D2) — thay thế hoàn toàn
  `/form` cũ.
- Nội dung tĩnh khác (hero copy, ưu điểm, quy trình, đánh giá, footer) — theo đúng nội dung tiếng Việt
  trong file design (mục 2, giữ nguyên câu chữ theo yêu cầu gốc của file thiết kế).

## Không nằm trong Plan 2 (kể cả khi pivot)

- Trang "Giáo viên & Xe" thật (theo quyết định #6).
- Thanh toán/hoá đơn thật — doanh thu chỉ là số ước tính hiển thị.
- Đăng ký tài khoản học viên tự phục vụ (self-service login cho học viên) — hệ thống hiện tại chỉ có admin
  login, học viên chỉ "đăng ký" (submission), không có tài khoản.

## Kiểm chứng (khi triển khai từng phase)

Theo đúng pattern đã dùng xuyên suốt project: mỗi phase build+test riêng
(`./gradlew clean build` cho backend, `npm run build`/`npm test` cho frontend), verify thủ công qua
curl/Postman hoặc UI thật trước khi sang phase kế, cập nhật `CHECKLIST.md` theo từng phase — **không**
merge D1→D6 thành 1 khối lớn không kiểm chứng được từng phần.

## Ước lượng độ lớn tương đối

D1 (Course backend) và D2 (extend Submission) là phần rủi ro/công sức lớn nhất (migration dữ liệu thật).
D3 (dashboard aggregation) vừa. D4–D6 (frontend) phần lớn là lặp lại pattern đã có sẵn trong project
(list/detail/filter/pagination đã làm cho Submission, chỉ áp dụng lại cho Course), nên nhanh hơn tương
đối so với công sức thiết kế schema ở D1/D2.
