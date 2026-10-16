package com.gmc.backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 30, message = "Phone must be at most 30 characters")
    private String phone;

    @Size(max = 255, message = "Address must be at most 255 characters")
    private String address;
}
