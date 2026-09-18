package com.register.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The admin-configurable, non-transactional dashboard settings. {@code passRatePercent} is not computed
 * from any real data - nothing in this system tracks exam results - it is entered directly by an admin.
 * Both {@code passRatePercent} and {@code examCount} are {@code null} until an admin sets them via
 * {@code PATCH /api/admin/dashboard/settings}.
 *
 * @param passRatePercent the admin-entered pass rate, as a percentage (0-100), or {@code null} if not yet
 *                         configured
 * @param examCount       the admin-entered exam count the pass rate is based on, or {@code null} if not
 *                         yet configured
 * @param updatedAt        when these settings were last changed
 */
public record DashboardSettingsResponse(
        BigDecimal passRatePercent,
        Integer examCount,
        LocalDateTime updatedAt
) {
}
