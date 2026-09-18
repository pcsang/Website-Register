-- Extends submissions with a course link and a 4-state status model (Phase D2 - extend Submission), see
-- docs/planning/plan-2-full-redesign-driveup.md.
--
-- Old -> new status data map (per that plan's "Decisions Needed" table, item 2):
--   NEW         -> PENDING_CONSULTATION
--   IN_PROGRESS -> IN_PROGRESS (unchanged, the name is reused as-is in the new 4-state set)
--   COMPLETED   -> GRADUATED
-- CONFIRMED is a brand-new state with no historical equivalent - nothing maps to it from existing rows,
-- that is expected.
--
-- The OLD CHECK constraint is dropped BEFORE the data UPDATEs (not after) - PostgreSQL enforces a CHECK
-- constraint on every row-level UPDATE, not just on commit, so remapping a row to a brand-new value like
-- 'PENDING_CONSULTATION' while the old ('NEW','IN_PROGRESS','COMPLETED')-only constraint is still active
-- would itself violate that old constraint. Dropping it first means no constraint is active during the
-- UPDATEs (so nothing can violate anything), and the NEW constraint is only added afterwards, once every
-- row already holds one of its allowed values - so it also validates successfully on the first try. The
-- whole migration runs inside one Flyway-managed transaction, so a failure at any step rolls back cleanly
-- with zero partial data changes (verified in practice - see CHECKLIST.md's D2 log entry).

ALTER TABLE submissions ADD COLUMN course_id BIGINT;
ALTER TABLE submissions
    ADD CONSTRAINT fk_submissions_course FOREIGN KEY (course_id) REFERENCES courses (id);

ALTER TABLE submissions DROP CONSTRAINT submissions_status_check;

UPDATE submissions SET status = 'PENDING_CONSULTATION' WHERE status = 'NEW';
UPDATE submissions SET status = 'GRADUATED' WHERE status = 'COMPLETED';

ALTER TABLE submissions
    ADD CONSTRAINT submissions_status_check
        CHECK (status IN ('PENDING_CONSULTATION', 'CONFIRMED', 'IN_PROGRESS', 'GRADUATED'));
