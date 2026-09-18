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
 * Request body for creating a new course. Validation constraints mirror {@link com.register.backend.entity.Course}'s
 * DB column constraints exactly.
 */
public record CreateCourseRequest(

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
