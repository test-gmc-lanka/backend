package com.gmc.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** A message from the public "Contact us" page. */
@Data
public class ContactRequest {

    @NotBlank(message = "Please enter your name")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @NotBlank(message = "Please enter your email so we can reply")
    @Email(message = "Enter a valid email address")
    @Size(max = 150, message = "Email must be at most 150 characters")
    private String email;

    // Optional
    @Pattern(regexp = "^$|^\\+?[0-9 ()-]{9,20}$", message = "Enter a valid phone number")
    private String phone;

    @NotBlank(message = "Please choose a subject")
    @Size(max = 120, message = "Subject must be at most 120 characters")
    private String subject;

    @NotBlank(message = "Please write your message")
    @Size(min = 10, max = 2000, message = "Message must be 10–2000 characters")
    private String message;
}
