-- Adds the dashboard_settings table (Phase D3 - Dashboard overview expansion), see
-- docs/planning/plan-2-full-redesign-driveup.md.
--
-- Single-row settings table (fixed id = 1, no auto-generated identity - there is only ever one row).
-- pass_rate_percent is an admin-entered value, NOT derived from any transactional data - nothing in this
-- system tracks real exam results (plan's "Decisions Needed" item 4). NULL by default means "not yet
-- configured"; a fake realistic-looking default (e.g. 98.2) would misrepresent real data, same trap the
-- D1 seatsRegistered-placeholder decision correctly avoided. exam_count is an optional companion figure
-- for the design mockup's "over N exams taken" framing, also NULL until an admin sets it.
-- (Note: deliberately ASCII-only in this comment - a non-ASCII character here previously tripped a
-- WIN1252-vs-UTF8 client-encoding mismatch against this machine's local PostgreSQL instance.)

CREATE TABLE dashboard_settings (
    id                 BIGINT PRIMARY KEY,
    pass_rate_percent  NUMERIC(5, 2) CHECK (pass_rate_percent IS NULL OR (pass_rate_percent >= 0 AND pass_rate_percent <= 100)),
    exam_count         INTEGER CHECK (exam_count IS NULL OR exam_count >= 0),
    updated_at         TIMESTAMP(6) NOT NULL
);

INSERT INTO dashboard_settings (id, pass_rate_percent, exam_count, updated_at)
VALUES (1, NULL, NULL, CURRENT_TIMESTAMP);
