package com.gmc.backend.service;

import com.gmc.backend.dto.request.LoginRequest;
import com.gmc.backend.dto.request.RegisterRequest;
import com.gmc.backend.dto.response.AuthResponse;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request, HttpServletResponse response);

    AuthResponse refresh(String rawRefreshToken, HttpServletResponse response);

    void logout(String rawRefreshToken, HttpServletResponse response);
}
