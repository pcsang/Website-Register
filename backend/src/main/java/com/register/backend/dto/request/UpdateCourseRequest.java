package com.register.backend.dto.request;

import com.register.backend.enums.LicenseClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for updating an existing course. Covers every editable field as a full replacement (not a
 * partial patch) - the same validation constraints as {@link CreateCourseRequest}, so a value that would
 * be rejectable by the database is never accepted here either.
 */
public record UpdateCourseRequest(

        @NotBlank
        @Size(max = 200)
        String name,

        @NotNull
        LicenseClass licenseClass,

        @NotNull
        @PositiveOrZero
        BigDecimal price,

        @NotNull
        @Positive
        Integer durationMonths,

        @NotNull
        @Positive
        Integer practiceHours,

        @Size(max = 2000)
        String description,

        @Size(max = 100)
        String branch,

        @Size(max = 200)
        String teacherName,

        @NotNull
        @Positive
        Integer seatsTotal,

        @NotNull
        LocalDate startDate

) {
}
