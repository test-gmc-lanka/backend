package com.gmc.backend.service;

import com.gmc.backend.model.User;

/**
 * Carries the new raw token (for the HTTP-only cookie) and the owning user
 * (to generate a new access token) after a successful rotation.
 */
public record RefreshTokenResult(String rawToken, User user) {
}
