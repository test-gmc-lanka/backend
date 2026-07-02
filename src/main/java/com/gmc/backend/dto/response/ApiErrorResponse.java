package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiErrorResponse {

    private boolean success;
    private String error;
    private String message;
}
