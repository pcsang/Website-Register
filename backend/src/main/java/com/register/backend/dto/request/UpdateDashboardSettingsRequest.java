package com.register.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Request body for updating the admin-configured dashboard settings (full replacement of both editable
 * fields, mirroring this project's other update DTOs, e.g. {@code UpdateCourseRequest}).
 *
 * @param passRatePercent the pass rate to set, as a percentage between 0 and 100 inclusive; required - a
 *                        {@code PATCH} is expected to supply the value being set
 * @param examCount       the exam count the pass rate is based on; optional, {@code null} to clear it
 */
public record UpdateDashboardSettingsRequest(

        @NotNull
        @DecimalMin(value = "0")
        @DecimalMax(value = "100")
        BigDecimal passRatePercent,

        @PositiveOrZero
        Integer examCount

) {
}
