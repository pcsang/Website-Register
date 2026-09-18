package com.register.backend.enums;

/**
 * Derived (never persisted) seat-availability label for a {@link com.register.backend.entity.Course},
 * computed from {@code seatsRegistered}/{@code seatsTotal} - "Còn chỗ" (&lt; 70% full), "Sắp đầy"
 * (70-99% full), "Đã đầy" (100%+ full). English enum names, Vietnamese display left to the frontend, same
 * convention as {@link SubmissionStatus}/{@link LicenseClass}.
 */
public enum CourseAvailabilityStatus {
    AVAILABLE,
    FILLING_UP,
    FULL
}
