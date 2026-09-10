package com.taskmanagement.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountSecurityRequest(
        @Size(max = 128) String currentPassword,
        @Size(max = 8192) String googleCredential,
        @Pattern(regexp = "[A-Za-z0-9_]{3,50}", message = "Username must contain 3-50 letters, digits or underscores") String username,
        @Size(min = 15, max = 72) String newPassword,
        @Size(max = 72) String confirmPassword
) { }
