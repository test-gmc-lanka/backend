package com.gmc.backend.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Email(message = "Valid email is required")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
    private String password;

    @Size(max = 30, message = "Phone must be at most 30 characters")
    private String phone;
    @Size(max = 255, message = "Address must be at most 255 characters")
    private String address;

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "(?i)CUSTOMER|TECHNICIAN|ADMIN", message = "Role must be CUSTOMER, TECHNICIAN or ADMIN")
    private String role;
}
