# Prompt cho Claude CLI — Implement Landing Page & Admin Dashboard (DriveUp)

> Copy toàn bộ nội dung dưới đây, chạy `claude` trong thư mục gốc project Angular của bạn, rồi paste vào.
> Prompt được viết theo đúng thiết kế đã duyệt (mockup) — không tự sáng tạo lại UI, chỉ hiện thực hoá bằng Angular.

---

## Bối cảnh dự án

Đây là một Angular app hiện có (backend Java riêng). Nhiệm vụ: thêm vào codebase hiện tại 2 phần UI mới:

1. **Landing page công khai** — trang đăng ký thông tin học lái xe (route `/`, không cần đăng nhập).
2. **Admin dashboard** — 3 trang quản trị nằm sau layout có sidebar (route `/admin/...`).

**Chỉ làm phần frontend UI với mock data** (arrays/objects khai báo cứng trong service hoặc file `*.mock.ts`). Chưa gọi API thật — nhưng hãy tách data ra khỏi component (đặt trong service có method trả `Observable`/`Promise`/mảng) để sau này dễ thay bằng HTTP call tới backend Java mà không phải sửa template.

**Trước khi code**: hãy tự khảo sát cấu trúc project hiện tại trước (Angular version, standalone components hay NgModule, style hiện dùng SCSS thuần/Tailwind/Angular Material/PrimeNG, cấu trúc thư mục `src/app/...`, coding convention, lint rules) và làm theo đúng convention đó. Nếu project chưa có Tailwind và bạn thấy thêm Tailwind hợp lý, hỏi tôi trước khi cài thêm dependency mới; mặc định dùng SCSS thuần theo biến thiết kế bên dưới nếu không chắc.

---

## 1. Design tokens (bắt buộc dùng đúng, để UI khớp mockup)

```scss
// Màu sắc
$color-bg: #F7F8FB;           // nền trang landing
$color-bg-admin: #F4F5FA;     // nền content admin
$color-surface: #FFFFFF;
$color-ink: #12172B;          // text chính / sidebar bg
$color-muted: #5B6478;
$color-muted-2: #8A93A8;
$color-border: #E7E9F3;
$color-border-2: #E1E4EF;

$color-primary: #2B5FFF;      // xanh dương chủ đạo (nút CTA, active state)
$color-primary-bg: #EAF0FF;
$color-accent-orange: #F97316;
$color-accent-orange-bg: #FFF1E8;
$color-success: #16A34A;
$color-success-bg: #EAFBF0;
$color-warning: #B7791F;
$color-warning-bg: #FFF6E5;
$color-danger: #DC2626;
$color-danger-bg: #FDECEC;
$color-purple: #7C3AED;
$color-purple-bg: #F4EEFF;

// Sidebar admin (nền tối)
$sidebar-bg: #12172B;
$sidebar-item-active-bg: #1E2440;
$sidebar-text: #9AA1B8;
$sidebar-text-muted: #5C6480;

// Typography — Google Fonts: Sora (heading, weight 600/700/800), Manrope (body, weight 400–800)
$font-heading: 'Sora', sans-serif;
$font-body: 'Manrope', sans-serif;

// Bo góc & khoảng cách
$radius-sm: 8px; $radius-md: 12px; $radius-lg: 16px; $radius-xl: 20px; $radius-pill: 999px;
$container-padding-desktop: 80px; // landing
$content-padding-admin: 36px;
```

Nguyên tắc thiết kế chung: **không dùng gradient, không dùng card viền trái (left-border card), không dùng emoji, không dùng icon set Inter/Material mặc định** — dùng icon **inline SVG stroke** (line-based, 1.8–2px stroke, bo tròn), tự vẽ hoặc lấy từ bộ icon dạng outline tương tự (Lucide/Feather đều hợp phong cách này, có thể cài `lucide-angular` nếu project chưa có icon lib). Toàn bộ nút/link phải là phần tử thật (`<button>`, `<a>`), input có `<label>` gắn `for`, để đảm bảo accessibility.

---

## 2. Trang Landing (route `/`, component ví dụ `LandingPageComponent`)

Bố cục 1 trang dài, cuộn dọc, các section theo thứ tự — **nội dung tiếng Việt giữ nguyên như dưới**, không đổi câu chữ:

1. **Nav bar** (sticky top, nền trắng, border-bottom): logo "DriveUp" (icon xe hơi SVG) bên trái · menu giữa: Ưu điểm (`#features`) / Khoá học (`#courses`) / Quy trình (`#process`) / Đánh giá (`#reviews`) · bên phải: số hotline "1900 2025" + nút CTA pill "Đăng ký ngay" (nền `$color-primary`, chữ trắng) scroll tới `#dangky`.

2. **Hero**: 2 cột.
   - Trái: badge nhỏ "Trung tâm đào tạo lái xe hàng đầu TP.HCM" (nền `$color-primary-bg`, chữ `$color-primary`, có icon sao) · H1 "Học lái xe an toàn, đậu ngay lần đầu" (56px, weight 800) · đoạn mô tả ngắn · 2 nút: "Đăng ký học ngay" (primary, filled) và "Xem khoá học" (outline) · hàng 3 số liệu: 15.000+ Học viên đã tốt nghiệp / 98% Tỷ lệ đậu lần đầu / 12 Chi nhánh toàn quốc.
   - Phải: khung minh hoạ bo góc lớn (nền `$color-primary-bg`) chứa icon xe hơi lớn + 1 badge nổi "Đã đăng ký thành công!" (icon check xanh lá) đặt góc dưới trái.

3. **Ưu điểm** (`#features`): tiêu đề section "Vì sao chọn DriveUp" + H2 "Đồng hành cùng bạn từ ngày đầu đến khi cầm bằng" · grid 4 cột, mỗi card: icon trong khung vuông bo góc màu nhạt riêng, tiêu đề, mô tả:
   - Giáo viên giàu kinh nghiệm — icon nhóm người, nền xanh dương nhạt
   - Xe tập lái đời mới — icon xe hơi, nền cam nhạt
   - Lịch học linh hoạt — icon lịch, nền xanh lá nhạt
   - Hỗ trợ hồ sơ trọn gói — icon check tròn, nền tím nhạt

4. **Khoá học** (`#courses`, nền trắng): tiêu đề "Khoá học" + H2 "Chọn hạng bằng phù hợp với bạn" · grid 3 card giá:
   - **Hạng B1** (Ô tô số tự động) — 7.500.000đ — 3 tháng, 16 giờ thực hành, hỗ trợ thi lý thuyết & thực hành — nút outline "Chọn khoá học"
   - **Hạng B2** (Ô tô đến 9 chỗ) — card này có border 2px màu primary + badge nổi "Phổ biến nhất" — 9.800.000đ — 4 tháng, 24 giờ thực hành + sa hình, xe đưa đón điểm tập trung — nút filled "Chọn khoá học"
   - **Hạng C** (Xe tải trên 3.5 tấn) — 13.200.000đ — 5 tháng, thực hành xe tải thực tế, giáo viên kèm riêng — nút outline "Chọn khoá học"

5. **Quy trình** (`#process`): tiêu đề "Quy trình" + H2 "Chỉ 4 bước để bắt đầu" · 4 bước ngang, mỗi bước có số tròn (bước 1 tô đậm màu primary, các bước sau nền nhạt): Đăng ký thông tin → Tư vấn & chọn khoá → Đóng học phí → Bắt đầu học (kèm mô tả ngắn mỗi bước).

6. **Đánh giá** (`#reviews`, nền trắng): tiêu đề "Đánh giá" + H2 "Học viên nói gì về DriveUp" · 3 card testimonial (nền xám nhạt, bo góc), mỗi card: 5 sao vàng, đoạn quote, avatar tròn màu + tên + vai trò (Nguyễn Thị Mai – Học viên hạng B2, Trần Văn Khoa – Học viên hạng B1, Lê Hoàng Phúc – Học viên hạng C).

7. **Form đăng ký** (`#dangky`): 2 cột.
   - Trái: tiêu đề "Đăng ký tư vấn" + H2 "Để lại thông tin, tư vấn viên sẽ liên hệ ngay" + mô tả + danh sách liên hệ (hotline, email, địa chỉ) kèm icon.
   - Phải: **form card** (bo góc, đổ bóng nhẹ) gồm field **Họ và tên** (text, required), **Số điện thoại** (tel, required, validate số VN), **Email** (email, required, validate format), **Hạng bằng muốn học** (select: Hạng B1 / Hạng B2 / Hạng C) → nút submit filled "Gửi đăng ký" full-width. **Khi submit hợp lệ**: ẩn form, hiện trạng thái thành công (icon check tròn xanh lá + tiêu đề "Đăng ký thành công!" + mô tả cảm ơn) — dùng `*ngIf`/state trong component, có thể dùng Reactive Forms (`FormGroup` + `Validators`) để xử lý validate trước khi coi là "submit thành công".

8. **Footer** (nền tối `$color-ink`, chữ sáng): 4 cột — logo+mô tả ngắn / cột "Khoá học" (link tới B1/B2/C) / cột "Công ty" (Về chúng tôi, Đánh giá, Liên hệ) / cột "Liên hệ" (hotline, email, địa chỉ) — dưới cùng dòng copyright + số giấy phép, có `border-top` phân cách.

---

## 3. Admin Dashboard (layout dùng chung + 3 page)

### Layout chung `AdminLayoutComponent`
- **Sidebar trái** cố định 264px, nền `$sidebar-bg`, gồm: logo "DriveUp Admin" trên cùng · nhóm menu chính (label "MENU CHÍNH"): **Tổng quan** (icon grid, route `/admin/overview`), **Học viên đăng ký** (icon users, route `/admin/students`), **Khoá học & Lịch học** (icon calendar, route `/admin/courses`) — dùng `routerLink` + `routerLinkActive` để tô sáng mục đang chọn (nền `$sidebar-item-active-bg`, chữ trắng); mục "Giáo viên & Xe" hiển thị dạng disabled/coming-soon (màu chữ tối hơn, không click được) — không cần route thật. Cuối sidebar: mục "Cài đặt" + block thông tin user (avatar tròn khởi tạo chữ "TA", tên "Trần Anh", vai trò "Quản trị viên", icon logout).
- **Topbar** trong content area (không phải trong sidebar) cao 76px, nền trắng, border-bottom: ô search bên trái (icon kính lúp + input placeholder tuỳ trang) · bên phải: icon chuông thông báo (có chấm đỏ báo mới) + block avatar/tên/role giống sidebar.
- **Router outlet** bên dưới topbar, padding 32px 36px, nền `$color-bg-admin`.

### 3.1 `/admin/overview` — Tổng quan
- Header: "Chào buổi sáng, Anh 👋" + dòng phụ ngày hiện tại + mô tả · nút outline "Xuất báo cáo" (icon download) bên phải.
- **4 KPI card** dạng lưới 4 cột, mỗi card: label mờ, icon trong khung màu riêng, số liệu lớn (font Sora, 28px, 800), dòng phụ (mũi tên tăng trưởng xanh hoặc text phụ):
  - Học viên mới (tháng): 248, +12,4% so với tháng trước
  - Doanh thu (tháng): 2,16 tỷ, +8,1% so với tháng trước
  - Khoá đang mở: 18, "6 khai giảng tuần này"
  - Tỷ lệ đậu: 98,2%, "Trên 640 lượt thi trong quý"
- **Hàng 2 cột**: (trái, rộng hơn) card "Đăng ký theo tháng" — bar chart đơn giản 6 cột (T4–T9), cột cuối cùng (tháng hiện tại) tô đậm màu primary, các cột khác màu nhạt hơn — **implement bằng thư viện chart nếu project đã có sẵn (ví dụ ngx-charts/Chart.js), nếu chưa có thì vẽ bằng div/flexbox thuần theo % chiều cao như mock, không cần thêm dependency mới nếu không cần thiết**; (phải) card "Lịch khai giảng sắp tới" — danh sách 4 dòng, mỗi dòng: khung ngày (tháng/ngày) + tên khoá + số chỗ đã đăng ký/tổng, có link "Xem tất cả" → `/admin/courses`.
- **Bảng "Đăng ký gần đây"** cuối trang: cột Họ tên / Số điện thoại / Khoá học / Ngày đăng ký / Trạng thái (badge màu theo trạng thái: xanh lá = Đã xác nhận, vàng = Chờ tư vấn), link "Xem toàn bộ danh sách" → `/admin/students`.

### 3.2 `/admin/students` — Học viên đăng ký
- Header: "Học viên đăng ký" + dòng phụ tổng số · nút filled "+ Thêm học viên".
- **Toolbar**: ô tìm kiếm (icon search, placeholder "Tìm theo tên, số điện thoại...") · select "Tất cả khoá học" (B1/B2/C) · select "Tất cả trạng thái" (Chờ tư vấn/Đã xác nhận/Đang học/Đã tốt nghiệp) · nút "Bộ lọc" (icon filter) đẩy sang phải.
- **Bảng** cột: Họ tên (kèm avatar tròn màu ngẫu nhiên) / Số điện thoại / Email / Khoá học / Ngày đăng ký / Trạng thái (badge màu theo trạng thái, 4 trạng thái như trên + xám cho "Đã tốt nghiệp") / Hành động (icon "xem" + icon "sửa").
- Dữ liệu mock ít nhất 6–10 dòng, đa dạng trạng thái.
- **Phân trang** cuối bảng: text "Hiển thị X–Y trong tổng số N học viên" + nút số trang (trang hiện tại tô màu primary).
- Nên implement search/filter chạy thật trên mock data (dùng `computed`/`filter()` trong component), không cần backend.

### 3.3 `/admin/courses` — Khoá học & Lịch học
- Header: "Khoá học & Lịch học" + dòng phụ · nút filled "+ Thêm khoá học".
- **Dải lịch tuần** (week strip): 7 ô Thứ 2→CN với số ngày, ngày hiện tại tô nền primary, có thể có chấm cam nhỏ đánh dấu ngày có sự kiện.
- **Toolbar**: select hạng bằng / select trạng thái (Còn chỗ, Sắp đầy, Đã đầy) / select chi nhánh / nút "Bộ lọc".
- **Bảng khoá học** cột: Tên khoá học (kèm dòng phụ hạng+loại xe) / Ngày khai giảng / Giáo viên phụ trách / Số chỗ (progress bar ngang + số "đã đăng ký/tổng", màu bar theo mức độ: xanh lá <70%, cam 70–99%, đỏ =100%) / Trạng thái (badge: xanh lá=Còn chỗ, vàng=Sắp đầy, đỏ=Đã đầy) / Hành động (icon menu 3 chấm).
- Phân trang giống trang Students.

---

## 4. Yêu cầu kỹ thuật

- **Routing**: `/` → Landing (không layout admin) · `/admin` → `AdminLayoutComponent` với children `overview` (default/redirect), `students`, `courses`.
- **Responsive**: ít nhất đảm bảo landing page dùng được tốt trên mobile (nav bar sập thành hamburger menu là điểm cộng nhưng không bắt buộc nếu không có trong mock — ưu tiên khớp desktop trước, sau đó làm responsive hợp lý cho các breakpoint phổ biến); admin dashboard tối thiểu hoạt động tốt ở desktop ≥1280px.
- **Mock data**: tạo `admin-mock.service.ts` (hoặc theo convention project) chứa: danh sách học viên, danh sách khoá học/lịch khai giảng, số liệu KPI tổng quan, dữ liệu biểu đồ theo tháng — để sau này dễ thay bằng `HttpClient` gọi Java backend.
- **Form đăng ký landing**: dùng Angular Reactive Forms, validate required + định dạng SĐT/email, disable nút submit khi form invalid, hiển thị thông báo lỗi field khi touched+invalid.
- **Component hoá**: tách các phần lặp lại thành component con nếu hợp lý (ví dụ `StatusBadgeComponent`, `KpiCardComponent`, `SidebarComponent`) thay vì viết lặp trong template — nhưng ưu tiên giữ đơn giản, không over-engineer cho một bản mock/demo.
- **Icon**: dùng 1 icon library nhất quán trong toàn bộ 2 phần này (không trộn nhiều style icon khác nhau).
- **Không** tự thêm section/feature ngoài spec trên (không bịa thêm trang, không thêm dark mode trừ khi tôi yêu cầu).

---

## 5. Việc cần làm (thứ tự gợi ý)

1. Khảo sát cấu trúc project hiện tại, xác nhận Angular version + styling convention trước khi tạo file mới.
2. Thêm font Sora + Manrope (Google Fonts) vào `index.html` hoặc styles global.
3. Tạo design tokens (SCSS variables hoặc CSS custom properties) theo mục 1.
4. Implement `LandingPageComponent` theo mục 2, route `/`.
5. Implement `AdminLayoutComponent` (sidebar + topbar) theo mục 3.
6. Implement 3 page admin (`overview`, `students`, `courses`) + mock service.
7. Chạy thử, tự kiểm tra: routing hoạt động, form validate đúng, bảng/filter hoạt động trên mock data, không có lỗi console.
8. Báo cáo lại: các file đã tạo/sửa, cách chạy thử (`ng serve` route nào xem trang nào), và phần nào còn là mock/giả lập cần nối API thật sau này.

Nếu có phần nào trong spec chưa rõ hoặc mâu thuẫn với convention hiện tại của project, hãy hỏi lại trước khi code thay vì tự đoán.
