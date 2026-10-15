package com.gmc.backend.service;

public interface PasswordResetService {

    // Emails a reset link if the address belongs to an active account; silent otherwise
    void requestReset(String email);

    void resetPassword(String rawToken, String newPassword);
}
