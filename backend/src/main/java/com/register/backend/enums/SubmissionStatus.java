package com.register.backend.enums;

/**
 * Lifecycle states of a {@link com.register.backend.entity.Submission}, from initial inquiry through
 * graduation. {@code CONFIRMED}, {@code IN_PROGRESS}, and {@code GRADUATED} are considered a "confirmed
 * seat" for a {@link com.register.backend.entity.Course} (see {@code CourseService}'s seatsRegistered
 * calculation); {@code PENDING_CONSULTATION} is not, since it represents an inquiry only.
 */
public enum SubmissionStatus {
    PENDING_CONSULTATION,
    CONFIRMED,
    IN_PROGRESS,
    GRADUATED
}
