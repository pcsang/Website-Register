package com.register.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubmissionRequest(

        @NotBlank
        @Size(max = 200)
        String fullName,

        @Email(message = "must be a valid email")
        @Size(max = 255)
        String email,

        @Size(max = 30)
        String phone,

        @Size(max = 2000)
        String message

) {
}
