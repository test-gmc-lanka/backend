package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String role;
    private Boolean active;
}
