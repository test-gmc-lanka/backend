package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String role;
    private String name;
    private Long userId;
}
