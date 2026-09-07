package com.register.backend.dto.request;

import com.register.backend.enums.SubmissionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for updating a submission's status.
 *
 * @param status the new status; must be one of {@link SubmissionStatus}'s values. An unrecognized string
 *               value fails JSON deserialization before this record is even constructed (handled as a 400
 *               by {@code GlobalExceptionHandler}); a present-but-null value is caught by {@code @NotNull}.
 */
public record UpdateSubmissionStatusRequest(

        @NotNull
        SubmissionStatus status

) {
}
