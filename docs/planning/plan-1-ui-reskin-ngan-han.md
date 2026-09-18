# Plan 1 — Reskin UI theo design DriveUp (ngắn hạn)

**Khuyến nghị: làm plan này trước**, bất kể sau này có quyết định pivot sang Plan 2 hay không. Đây là
thay đổi thuần visual, rủi ro thấp, không đụng domain/API, và là nền tảng bắt buộc dù chọn hướng nào.

## Bối cảnh & mục tiêu

File `driveup-claude-cli-prompt-design-UI-UX.md` mô tả một design system hoàn toàn mới (màu xanh dương
`#2B5FFF`, font Sora/Manrope, icon outline kiểu Lucide/Feather, layout sidebar tối cho admin, không
gradient, không card viền trái) cho một sản phẩm khác (trường dạy lái xe "DriveUp"). Plan này **không**
đổi nghiệp vụ — giữ nguyên domain hiện tại (`Submission`: fullName/email/phone/message/status, 3 route
`/form`, `/admin/dashboard`, `/admin/submissions/:id` + `/admin/login`) — chỉ thay lớp giao diện
(màu sắc, typography, icon, bố cục card/table/sidebar) để khớp ngôn ngữ thiết kế mới, dựa trên cấu trúc
Angular 19 standalone + Angular Material đã có.

**Không nằm trong phạm vi plan này** (xem Plan 2 nếu muốn làm):
- Trang landing nhiều section (hero/ưu điểm/khoá học/quy trình/đánh giá) với nội dung tiếng Việt về học
  lái xe — nội dung đó gắn với domain "trường lái xe", không khớp domain hiện tại. Trang `/form` vẫn là
  1 form đơn, chỉ đổi style.
- Trang "Khoá học & Lịch học" — không có khái niệm Course trong backend hiện tại.
- Biểu đồ "Đăng ký theo tháng" — backend hiện không trả dữ liệu theo tháng (`DashboardSummaryResponse`
  chỉ có tổng số hiện tại, không có breakdown lịch sử).
- Chỉ số doanh thu, tỷ lệ đậu thi — không có dữ liệu nguồn.

## Cách tiếp cận

Giữ Angular Material làm nền tảng component thật (button, table, select, paginator, snackbar — đổi hết
sẽ tốn công lớn không tương xứng với một bản "reskin ngắn hạn"), chỉ đổi:
1. Design tokens (CSS custom properties + Material theme).
2. Font.
3. Icon set (Material Icons font → outline icon, ở những chỗ dùng làm điểm nhấn UI, không đổi
   toàn bộ mat-icon nội bộ của Material component).
4. Bố cục admin: từ top-nav header hiện tại sang sidebar tối + topbar theo đúng cấu trúc mock.
5. Sửa các điểm vi phạm nguyên tắc thiết kế mới đã phát hiện trong code hiện tại (card viền trái).

## 1. Design tokens

Sửa `clientUI/src/styles.scss` — thay toàn bộ khối `:root { ... }` hiện tại (palette xanh lá/terracotta)
bằng token mới, giữ đúng tên biến hiện có để không phải sửa lại mọi chỗ đang dùng `var(--color-primary)`
v.v.:

| Biến hiện tại | Giá trị mới (theo design DriveUp) |
|---|---|
| `--color-primary` | `#2B5FFF` |
| `--color-primary-dark` | tối hơn 1 tông của `#2B5FFF` (tự tính hoặc lấy `#1E46CC`) |
| `--color-primary-light` / nền primary nhạt | `#EAF0FF` (`$color-primary-bg`) |
| `--color-accent` | `#F97316` (`$color-accent-orange`) |
| `--color-bg` | `#F7F8FB` (landing) / `#F4F5FA` (admin content) — **2 giá trị khác nhau theo context**, thêm biến mới `--color-bg-admin` |
| `--color-surface` | `#FFFFFF` |
| `--color-text` | `#12172B` (`$color-ink`) |
| `--color-text-muted` | `#5B6478` |
| `--color-border` | `#E7E9F3` |
| `--radius-sm/md/lg` | `8px / 12px / 16px` (thêm `--radius-xl: 20px`, `--radius-pill: 999px`) |
| status badge colors | map lại theo `$color-success/$color-warning/$color-danger/$color-purple` ở mục 1 file design |
| *mới* `--sidebar-bg` | `#12172B` |
| *mới* `--sidebar-item-active-bg` | `#1E2440` |
| *mới* `--sidebar-text` / `--sidebar-text-muted` | `#9AA1B8` / `#5C6480` |

**Bỏ**: `--shadow-soft`/`--shadow-soft-lg` hiện dùng cho hiệu ứng đổ bóng đậm kiểu "landscaping" — design
mới chuộng shadow rất nhẹ, gần phẳng. Giảm độ đậm hoặc thay bằng `box-shadow` nhẹ hơn khi áp dụng cho
card/form.

## 2. Typography

- `clientUI/src/index.html`: đổi Google Fonts `<link>` từ Poppins+Inter sang **Sora** (600/700/800) +
  **Manrope** (400–800).
- `clientUI/src/styles.scss`: đổi `--font-heading`/`--font-body` sang `'Sora', sans-serif` /
  `'Manrope', sans-serif`; cập nhật `typography.brand-family`/`plain-family` trong khối
  `@include mat.theme(...)` tương ứng.

## 3. Material theme (màu)

Palette xanh dương `#2B5FFF` **không trùng** với bất kỳ palette dựng sẵn nào của Angular Material
(`mat.$green-palette`, `mat.$blue-palette` mặc định lệch tông so với `#2B5FFF`) — khác với lần reskin
xanh lá trước (dùng thẳng `mat.$green-palette`). Cần tạo palette M3 tuỳ chỉnh từ đúng mã màu này:
- Dùng schematic `ng generate @angular/material:theme-color` (nếu có mạng, sẽ hỏi 1 màu hex và sinh ra
  file token) **hoặc** xuất palette từ Material Theme Builder (material.angular.io/tools/theme-builder)
  dán mã `#2B5FFF` rồi copy token vào `styles.scss`.
- Áp dụng cùng cơ chế `@include mat.theme(...)` đã có sẵn — chỉ đổi `primary`/`tertiary` sang palette mới
  thay vì `mat.$green-palette`/`mat.$orange-palette`.

Xác minh bằng cách build xong kiểm tra `--mat-sys-primary` trong CSS output có đúng lệch không quá nhiều
so với `#2B5FFF` (giống cách đã xác minh ở lần reskin trước).

## 4. Icon set

Thêm `lucide-angular` (thư viện icon outline nhẹ, chính design doc gợi ý) làm dependency mới — **đây là
điểm cần xác nhận với bạn trước khi cài** (dependency mới, dù nhỏ và đơn mục đích).
- Icon trang trí (nav, KPI card, field prefix, sidebar menu, nút "Bộ lọc"...) → chuyển sang
  `<lucide-icon>` thay `<mat-icon>` với icon Material Icons font.
- **Không đổi** icon nội bộ của chính Angular Material component (ví dụ icon dropdown arrow của
  `mat-select`, icon calendar nếu có `mat-datepicker`) — những cái đó là một phần hành vi component, đổi
  sẽ phải override sâu, không đáng cho bản reskin ngắn hạn.

## 5. Bố cục Admin — Sidebar + Topbar

Thay header top-nav chung hiện tại (`AppComponent` — brand + Form/Admin Dashboard/Login-Logout) bằng 2
layout riêng biệt:
- **Public** (`/form`): giữ 1 header đơn giản (brand + link) hoặc bỏ hẳn header, chỉ còn nav bar kiểu
  landing (logo trái, không cần menu giữa vì không có section để scroll-to).
- **Admin** (`/admin/**`): `AdminLayoutComponent` mới (`clientUI/src/app/admin/layout/` hoặc
  `core/layout/`) — sidebar trái 264px nền `--sidebar-bg`, menu 2 mục thật (**Tổng quan** →
  `/admin/dashboard`, **Học viên đăng ký** → đổi tên hiển thị thành gì đó trung lập hơn cho domain hiện
  tại, ví dụ **"Submissions"**/"Danh sách đăng ký" → route hiện tại chính là `/admin/dashboard`'s table,
  cân nhắc giữ 1 route như cũ, không tách 2 route để không phá vỡ cấu trúc hiện có) dùng
  `routerLink`+`routerLinkActive`; topbar 76px chứa search (đấu nối lại đúng `searchControl` đã có ở
  `DashboardComponent`, không tạo logic search mới) + avatar/username (lấy từ `AuthService.username()` đã
  có sẵn) + nút logout (đấu `AuthService.logout()` đã có).
- `app.routes.ts`: bọc `admin/dashboard` và `admin/submissions/:id` trong 1 route cha dùng
  `AdminLayoutComponent` làm `component` cha + `children`, giữ nguyên `authGuard` trên các route con.
  `admin/login` **không** nằm trong layout này (trang login vẫn full-page, không sidebar).

## 6. Restyle từng trang (thuần template + scss, không đổi TS logic)

- **`information-form` (`/form`)**: bỏ gradient hero hiện tại (design mới không dùng gradient) → thay
  bằng nền phẳng `--color-primary-bg` hoặc `--color-ink` đặc, card form bo góc `--radius-lg`, input dùng
  style outline mảnh khớp token mới. Giữ nguyên 4 field + validator + spinner + snackbar.
- **`dashboard`**: **sửa 5 KPI card bỏ `border-left: 5px solid`** (đang vi phạm nguyên tắc "không card
  viền trái" của design mới) → đổi sang icon trong khung vuông bo góc màu nhạt riêng theo đúng mẫu mục
  3.1 file design (label mờ, icon-box, số lớn font Sora 800). Table: bỏ hover xanh lá hiện tại, badge
  status remap màu theo token mới, giữ nguyên cột/search/filter/pagination/logic.
- **`submission-detail`**: card layout theo token mới, icon field label chuyển sang lucide-icon, badge
  status dùng chung style với dashboard.
- **`login`**: card centered theo token mới (bo góc lớn hơn, shadow nhẹ hơn).

## 7. Component hoá (tuỳ chọn, không bắt buộc)

Nếu thấy lặp nhiều khi restyle, có thể tách `StatusBadgeComponent` (thay cho `.status-badge` class dùng
lặp ở 2 nơi) — nhưng **không bắt buộc** cho bản reskin, ưu tiên đơn giản đúng tinh thần "không
over-engineer" mà chính design doc cũng nhấn mạnh.

## File sẽ tạo/sửa (đại diện, không liệt kê hết)

- Sửa: `clientUI/src/styles.scss`, `clientUI/src/index.html`, `clientUI/package.json` (nếu thêm
  `lucide-angular`).
- Mới: `clientUI/src/app/admin/layout/admin-layout.component.{ts,html,scss}` (hoặc tên tương đương).
- Sửa: `clientUI/src/app/app.routes.ts` (bọc route admin trong layout cha), `app.component.*` (bớt phần
  nav admin nếu chuyển hẳn sang sidebar, giữ phần public).
- Sửa (chỉ template/scss): `dashboard.component.{html,scss}`, `submission-detail.component.{html,scss}`,
  `login.component.{html,scss}`, `information-form.component.{html,scss}`.

## Kiểm chứng

1. `npm run build` — build production phải pass (không lỗi budget nghiêm trọng phát sinh do font/icon
   lib mới — theo dõi cảnh báo bundle size hiện tại 729 kB / budget 500 kB, `lucide-angular` nếu dùng
   `LucideAngularModule.pick({...})` chỉ import icon cần dùng để không phình thêm nhiều).
2. `npm test` — 7/7 test hiện có phải vẫn pass (thay đổi thuần visual không được phá vỡ spec đã có; nếu
   `AdminLayoutComponent` mới ảnh hưởng tới cách `dashboard.component.spec.ts`/`submission-detail...spec.ts`
   khởi tạo component thì cần sửa test tương ứng, không phải sửa logic).
3. Kiểm tra bằng mắt qua `ng serve`: `/form`, `/admin/login`, `/admin/dashboard`, `/admin/submissions/:id`
   — xác nhận sidebar/topbar hiển thị đúng, KPI card không còn viền trái, màu/font đúng token mới.
4. Xác nhận luồng auth (login → guard → interceptor → logout) không bị ảnh hưởng — đây là thay đổi thuần
   layout/style, không đụng `AuthService`/`auth.guard.ts`/`auth.interceptor.ts`.

## Rủi ro / điểm cần xác nhận trước khi làm

1. **Thêm `lucide-angular`** — dependency mới, cần bạn xác nhận (design doc gốc cũng nói rõ phải hỏi
   trước khi thêm lib mới).
2. **Palette Material tuỳ chỉnh cho `#2B5FFF`** — cần công cụ generate token (Material Theme Builder hoặc
   schematic có mạng) thay vì dùng thẳng palette dựng sẵn như lần trước — mất thêm ít thời gian setup.
3. Không có sẵn dữ liệu để làm biểu đồ/doanh thu/tỷ lệ đậu — nếu vẫn muốn có "cảm giác" giống mock (ví dụ
   1 card thống kê thêm) sẽ phải bịa số liệu tĩnh hoặc bỏ hẳn — khuyến nghị **bỏ hẳn**, không hiển thị số
   liệu giả trong sản phẩm thật.
