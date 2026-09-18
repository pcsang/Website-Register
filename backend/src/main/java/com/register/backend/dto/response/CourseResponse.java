package com.register.backend.dto.response;

import com.register.backend.enums.CourseAvailabilityStatus;
import com.register.backend.enums.LicenseClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Course response shape. {@code seatsRegistered} is a live {@code COUNT} of {@code Submission} rows for
 * this course with a status counting as a confirmed seat (see {@code CourseService}) - never stored on
 * {@code Course} itself, to avoid a second source of truth. {@code availabilityStatus} is derived from
 * {@code seatsRegistered}/{@code seatsTotal} at read time ("Còn chỗ" &lt; 70%, "Sắp đầy" 70-99%, "Đã đầy"
 * 100%+), also never persisted.
 */
public record CourseResponse(
        Long id,
        String name,
        LicenseClass licenseClass,
        BigDecimal price,
        Integer durationMonths,
        Integer practiceHours,
        String description,
        String branch,
        String teacherName,
        Integer seatsTotal,
        long seatsRegistered,
        CourseAvailabilityStatus availabilityStatus,
        LocalDate startDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
