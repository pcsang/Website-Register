-- Seeds the 3 fixed course packages shown on the new public landing page's "Khoá học" section
-- (Phase D6 - Frontend landing page), see docs/planning/plan-2-full-redesign-driveup.md.
--
-- Without this seed, a fresh database would render an empty courses grid on first load. Exact
-- name/price/duration/practiceHours/description values match the mockup copy in
-- driveup-claude-cli-prompt-design-UI-UX.md section 2.4 verbatim (description holds each card's
-- trailing feature clause; the landing page combines it with the real durationMonths/practiceHours
-- fields at render time). `start_date` values are deliberately in the near future so
-- `GET /api/courses` (sorted startDate ASC) surfaces these 3 rows first on an otherwise-empty table.

INSERT INTO courses (name, license_class, price, duration_months, practice_hours, description, branch,
                      teacher_name, seats_total, start_date, created_at, updated_at)
VALUES
    ('Hạng B1 (Ô tô số tự động)', 'B1', 7500000.00, 3, 16,
     'Hỗ trợ thi lý thuyết & thực hành', 'Quận 1', 'Nguyễn Văn An', 40,
     CURRENT_DATE + INTERVAL '14 days', NOW(), NOW()),

    ('Hạng B2 (Ô tô đến 9 chỗ)', 'B2', 9800000.00, 4, 24,
     'Xe đưa đón điểm tập trung', 'Quận 1', 'Trần Thị Bình', 40,
     CURRENT_DATE + INTERVAL '21 days', NOW(), NOW()),

    ('Hạng C (Xe tải trên 3.5 tấn)', 'C', 13200000.00, 5, 20,
     'Giáo viên kèm riêng', 'Quận 3', 'Lê Minh Cường', 30,
     CURRENT_DATE + INTERVAL '28 days', NOW(), NOW());
