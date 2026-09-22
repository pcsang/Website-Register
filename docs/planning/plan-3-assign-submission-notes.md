# Plan 3 — Gán Submission cho tư vấn viên + Ghi chú nội bộ

**Trạng thái:** Toàn bộ 5 phase (A–E) đã triển khai và verify xong — xem log trong `CHECKLIST.md`. Phase A
(tạo/list tài khoản tư vấn viên), Phase B (gán submission cho tư vấn viên), Phase C (ghi chú nội bộ) là
backend; Phase D (trang Submission Detail) và Phase E (bảng Students) là frontend, hoàn tất 2026-09-19.

## Context

Người dùng đã chọn "gán submission cho tư vấn viên + ghi chú nội bộ" là tính năng ưu tiên tiếp theo (tác
động trực tiếp đến vận hành hằng ngày, không cần tích hợp dịch vụ ngoài như email/SMS). Khảo sát code cho
thấy hệ thống hiện tại **chỉ có đúng 1 tài khoản admin** (`AdminUserSeeder` chỉ seed 1 lần từ
`ADMIN_USERNAME`/`ADMIN_PASSWORD`, không có endpoint tạo/list tài khoản nào khác — xác nhận qua
`AdminUserRepository` chỉ có `findByUsername`). Vì vậy tính năng này thực chất gồm 2 lớp:

1. **Điều kiện tiên quyết**: cho phép tạo thêm tài khoản tư vấn viên (API-only theo quyết định của người
   dùng — chưa cần UI quản lý tài khoản).
2. **Tính năng chính**: gán 1 submission cho 1 tài khoản (`assignedToId`, có thể để trống/bỏ gán) + thêm/
   xem ghi chú nội bộ theo dòng thời gian trên từng submission.

**Quyết định thiết kế quan trọng** (nêu rõ để xác nhận, không âm thầm giả định): việc gán **chỉ mang tính
tổ chức/label**, KHÔNG giới hạn quyền truy cập — mọi tài khoản admin vẫn xem/sửa được mọi submission bất kể
đã gán cho ai. Lý do: `SecurityConfig` hiện chỉ có đúng 1 rule (`/api/admin/**` → `hasRole("ADMIN")`), không
có hạ tầng phân quyền theo tài nguyên; xây dựng việc đó là phạm vi của tính năng "phân quyền" mà người dùng
đã tự tách riêng, để sau. Ghi chú cũng thiết kế **append-only** (chỉ thêm, không sửa/xoá) — đơn giản nhất,
đúng tinh thần "nhật ký hoạt động".

Mọi thay đổi backend đều rơi vào `/api/admin/**` — **không cần sửa `SecurityConfig`**.

## Approach — theo đúng pattern hiện có của repo

Repo này có quy ước "một bước một lúc" rất rõ (xem `CHECKLIST.md`), nên triển khai tuần tự theo 5 phase nhỏ
bên dưới, build + test sau mỗi phase, thay vì làm 1 lần tất cả.

### Phase A — Backend: cho phép tạo/list tài khoản tư vấn viên (✅ đã xong)

- Migration: **không cần** — bảng `admin_users` đã tồn tại (V1), chỉ thêm code dùng nó.
- New: `dto/response/AdminUserSummaryResponse.java` (record `Long id, String username` — **không** bao giờ
  trả `passwordHash`, đúng nguyên tắc "never expose entities/secrets qua DTO").
- New: `dto/request/CreateAdminUserRequest.java` (record `username` + `password`,
  `@NotBlank @Size(max=100)` cho username, `@NotBlank @Size(min=8, max=100)` cho password — chặt hơn
  `LoginRequest` một chút vì đây là cấp phát credential mới, không phải validate lúc login).
- New: `service/AdminUserService.java` — 1 service riêng (giống `CourseService`/`AuthService`, mỗi entity/
  concern có service riêng, không gộp bừa). 2 method: `listAdminUsers()` (map toàn bộ qua
  `AdminUserSummaryResponse`, không phân trang — số lượng tư vấn viên nhỏ) và `createAdminUser(request)`
  (hash password bằng `PasswordEncoder` bean có sẵn — cùng bean `AuthService`/`AdminUserSeeder` đang dùng,
  **ép cứng `role = "ROLE_ADMIN"` server-side**, không nhận từ client, đúng pattern
  `createSubmission()` ép `status = PENDING_CONSULTATION`).
- New: `controller/AdminUserController.java` — `GET /api/admin/users`, `POST /api/admin/users`.
- `GlobalExceptionHandler`: thêm case `DataIntegrityViolationException` → 409 (username trùng vi phạm
  unique constraint hiện có trên `admin_users.username`).
- Test: unit test cho `AdminUserService` (Mockito, theo style `SubmissionServiceTest`) + `@WebMvcTest` cho
  `AdminUserController` (theo style `SubmissionControllerTest`/`AuthControllerTest`).

### Phase B — Backend: gán submission cho tư vấn viên (✅ đã xong)

- Migration mới `V6__add_submission_assignment.sql`:
  `ALTER TABLE submissions ADD COLUMN assigned_to_id BIGINT NULL REFERENCES admin_users (id);` — nullable,
  không FK cascade (chưa có flow xoá admin user nên chưa cần lo `ON DELETE`).
- `entity/Submission.java`: thêm field `assignedToId` (`Long`, plain column — **không** dùng
  `@ManyToOne`, đúng pattern `courseId` đã có trên chính entity này, không phá vỡ convention "FK phẳng, ID
  only" của Submission).
- `dto/response/SubmissionResponse.java`: thêm `Long assignedToId` (chỉ ID, giống hệt cách `courseId` đang
  làm — **không** join lấy username ở backend; frontend tự đối chiếu qua danh sách từ Phase A, đúng pattern
  hiện có của `courseId`/course list).
- New: `dto/request/AssignSubmissionRequest.java` (record `Long adminUserId` — cho phép `null` để bỏ gán,
  **không** đặt `@NotNull`).
- `SubmissionMapper`: thêm map field `assignedToId`.
- `SubmissionService`: thêm `assignSubmission(Long id, Long adminUserId)` — mirror y hệt `updateStatus()`:
  `findById().orElseThrow(ResourceNotFoundException)`, nếu `adminUserId != null` thì validate tồn tại qua
  `AdminUserRepository.findById().orElseThrow(...)`, set field, `saveAndFlush`, map response.
- `AdminSubmissionController`: thêm `PATCH /{id}/assign`.
- `SubmissionRepository.search(...)`: thêm tham số `assignedToId` filter (theo đúng pattern
  `(:param IS NULL OR ...)` đang dùng cho `status`/`courseId`); `listSubmissions()` +
  `AdminSubmissionController.listSubmissions()` thêm `@RequestParam Long assignedToId`.
- Test: cập nhật `SubmissionServiceTest`/`AdminSubmissionControllerTest`/integration test theo style hiện
  có (case gán thành công, gán vào id không tồn tại → 404, filter theo `assignedToId`).

### Phase C — Backend: ghi chú nội bộ (submission notes) (✅ đã xong)

- Cùng migration V6 (hoặc tách `V7` nếu muốn tách biệt theo bảng — **quyết định lúc code**, không ảnh hưởng
  kiến trúc): `CREATE TABLE submission_notes (id BIGSERIAL PRIMARY KEY, submission_id BIGINT NOT NULL
  REFERENCES submissions(id) ON DELETE CASCADE, author_id BIGINT NOT NULL REFERENCES admin_users(id),
  content TEXT NOT NULL, created_at TIMESTAMP NOT NULL);` — cascade khi xoá submission (dù hiện chưa có
  delete-submission, vẫn là default hợp lý cho bảng con), không cascade khi xoá admin user.
- New: `entity/SubmissionNote.java` — theo đúng style `Submission`/`AdminUser` (plain JPA, không Lombok,
  `@PrePersist` set `createdAt`, **không có `updatedAt`** vì append-only).
- New: `repository/SubmissionNoteRepository.java` — `findBySubmissionIdOrderByCreatedAtDesc(Long
  submissionId)` (mới nhất trước).
- New DTO: `dto/request/CreateSubmissionNoteRequest.java` (`content` `@NotBlank`), `dto/response/
  SubmissionNoteResponse.java` (`id, submissionId, authorId, authorUsername, content, createdAt`) —
  **có** `authorUsername` (không chỉ ID) vì ghi chú hiển thị "ai viết" trực tiếp trên UI, khác với
  `assignedToId` (chỉ là filter/label, resolve phía FE); denormalize username lúc tạo response tránh phải
  join lặp lại nhiều lần khi list.
- `SubmissionService`: thêm `listNotes(Long submissionId)` và `addNote(Long submissionId, String content,
  Authentication authentication)` — tác giả ghi chú lấy từ
  `adminUserRepository.findByUsername(authentication.getName())` (principal hiện có sẵn từ JWT, **không**
  cần đổi `LoginResponse`/JWT claims). Đặt trong `SubmissionService` (không tách `SubmissionNoteService`
  riêng) — ghi chú gắn chặt vòng đời 1 submission, không có consumer nào khác, tách riêng là over-engineer
  không cần thiết ở quy mô này.
- `AdminSubmissionController`: thêm `GET /{id}/notes`, `POST /{id}/notes` (lấy `Authentication` qua tham số
  controller, theo cách Spring MVC chuẩn — cần kiểm tra `JwtAuthenticationFilter` có set đủ
  `Authentication.getName()` = username hay không, đã xác nhận JWT `sub` = username nên OK).
- Test: `SubmissionServiceTest` (thêm/list note), `AdminSubmissionControllerTest` (mock service).

### Phase D — Frontend: trang Submission Detail (✅ đã xong)

- `models/submission.model.ts`: thêm `assignedToId: number | null` vào `Submission`; thêm
  `AssignSubmissionRequest { adminUserId: number | null }`.
- New `models/admin-user.model.ts`: `AdminUserSummary { id; username }`,
  `CreateAdminUserRequest { username; password }` (dùng nếu sau này có UI; giờ chỉ cần `AdminUserSummary`
  cho dropdown).
- New `models/submission-note.model.ts`: `SubmissionNote`, `CreateSubmissionNoteRequest`.
- New `core/services/admin-user.service.ts` — mirror `CourseService` (`providedIn: 'root'`, `inject()`,
  `environment.apiBaseUrl`), method `listAdminUsers()`.
- `core/services/submission.service.ts`: thêm `assignSubmission(id, adminUserId)`, `listNotes(id)`,
  `addNote(id, content)` — cùng pattern HttpClient với `updateStatus` hiện có.
- `admin/submission-detail/submission-detail.component.ts`:
  - Load `adminUserService.listAdminUsers()` song song với submission (giống cách `DashboardComponent` load
    summary/list độc lập — 1 lỗi không chặn lỗi kia).
  - Thêm `assignControl`, `assigning` flag, `isAssignDisabled()`, `assignSubmission()` — **mirror y hệt**
    `statusControl`/`updating`/`isUpdateDisabled()`/`updateStatus()` đã có (đã đọc full code, copy pattern
    1:1, kể cả cách gọi `extractErrorMessage()` sẵn có trong file).
  - Thêm `notes: SubmissionNote[]`, `notesLoading`, `noteControl` (FormControl content), `addingNote`,
    `addNote()` — load notes trong `ngOnInit`, reload sau khi thêm note thành công.
- `submission-detail.component.html`: thêm 1 dòng "Assigned to" vào `detail-grid` hiện có (mat-select, theo
  đúng style `status-update` block) + 1 section mới "Ghi chú nội bộ" (textarea + nút "Thêm ghi chú" + danh
  sách note hiển thị `authorUsername` + `createdAt | date:'medium'` + `content`, mới nhất trên đầu).

### Phase E — Frontend: bảng Students (list) (✅ đã xong)

- `core/services/submission.service.ts`: `listSubmissions()` thêm optional param `assignedToId`.
- `admin/students/students.component.ts`:
  - Load `adminUserService.listAdminUsers()` 1 lần, build `Map<number,string>` id→username để hiển thị cột
    (tương tự cách `courseControl` đã resolve course name — kiểm tra pattern chính xác lúc code).
  - Thêm `assignedToControl` (FormControl) mirror y hệt `statusControl`/`courseControl` — cùng
    `valueChanges` → `pageIndex = 0` → `loadSubmissions()`.
  - `displayedColumns`: chèn `'assignedTo'` giữa `'status'` và `'createdAt'`.
- `students.component.html`: thêm `ng-container matColumnDef="assignedTo"` (hiển thị username hoặc "—" nếu
  chưa gán) + thêm dropdown filter thứ 3 vào `.filters` div hiện có.

## Các file chính sẽ đụng tới

**Backend (mới):** `entity/SubmissionNote.java` ✅, `repository/SubmissionNoteRepository.java` ✅,
`service/AdminUserService.java` ✅, `controller/AdminUserController.java` ✅, 4 DTO record mới (2 đã xong ở
Phase A, 2 còn lại — `AssignSubmissionRequest`, `CreateSubmissionNoteRequest`, `SubmissionNoteResponse` —
✅ ở Phase B/C), `db/migration/V6__add_submission_assignment_and_notes.sql` ✅ (gộp cả Phase B lẫn Phase C
vào 1 migration duy nhất, như phương án đã nêu ở trên, thay vì tách V6/V7).
**Backend (sửa):** `entity/Submission.java` ✅, `dto/response/SubmissionResponse.java` ✅, `mapper/
SubmissionMapper.java` ✅, `service/SubmissionService.java` ✅, `controller/AdminSubmissionController.java` ✅,
`repository/SubmissionRepository.java` (query `search`) ✅, `exception/GlobalExceptionHandler.java` ✅ (case
409 đã thêm ở Phase A).
**Frontend (mới):** `models/admin-user.model.ts` ✅, `models/submission-note.model.ts` ✅, `core/services/
admin-user.service.ts` ✅.
**Frontend (sửa):** `models/submission.model.ts` ✅, `core/services/submission.service.ts` ✅,
`admin/submission-detail/submission-detail.component.{ts,html,scss}` ✅,
`admin/students/students.component.{ts,html,scss}` ✅.

## Verification (sau mỗi phase, đúng quy ước CLAUDE.md)

- Backend: `cd backend && .\gradlew clean build` (unit + integration test), test thủ công qua curl/Postman
  với JWT thật (login lấy token, gọi `POST /api/admin/users`, `PATCH .../assign`, `POST .../notes`).
- Frontend: `cd clientUI && ng build` (production config) + `ng test --watch=false --browsers=ChromeHeadless`
  + `ng serve` kiểm tra thủ công: tạo 1 tài khoản tư vấn viên qua API → vào Submission Detail gán submission
  cho tài khoản đó → thêm ghi chú → quay lại bảng Students xác nhận cột "Assigned to" và filter hoạt động.
- Cập nhật `CHECKLIST.md` theo đúng convention hiện có (mỗi phase 1 log entry) — việc này thuộc quy trình đã
  thiết lập sẵn của repo, không phải quyết định mới.

## Thứ tự triển khai

Làm **từng phase một** (A → B → C → D → E), build+test sau mỗi phase, dừng lại báo cáo trước khi sang phase
kế tiếp — đúng nguyên tắc "one step at a time" đã ghi trong `CLAUDE.md`, không tự động cascade hết 5 phase
trong 1 lần.
