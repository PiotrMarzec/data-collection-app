package com.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class EmailSubmissionRequest {

    @NotBlank(message = "Data ID is required")
    public String dataId;

    @NotBlank(message = "Signature is required")
    public String signature;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    public String email;
}
