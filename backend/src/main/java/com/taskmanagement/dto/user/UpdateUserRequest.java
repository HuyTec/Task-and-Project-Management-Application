package com.taskmanagement.dto.user;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @Size(max = 50, min=2, message = "Display name must be at most 50 characters long and at least 2 characters long")
    String displayName,

    @Null(message = "Username cannot be changed through this endpoint")
    String username,

    @Null(message = "Email cannot be changed through this endpoint")
    @Email(message = "Email is invalid")
    String email,

    @Null(message = "Use the account security endpoint to change your password")
    String password,

    @Size(max = 255, message = "Profile picture URL must be at most 255 characters long")
    @Pattern(regexp = "^$|^https://\\S+$", message = "Profile picture URL must use HTTPS")
    String profilePictureUrl
) {
}
