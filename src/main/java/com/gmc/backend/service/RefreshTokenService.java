package com.gmc.backend.service;

public interface RefreshTokenService {

    /**
     * Creates a new refresh token for the given user. Deletes all previous
     * tokens for that user. Returns the raw token to be sent in the cookie
     * (only the SHA-256 hash is persisted).
     */
    String createRefreshToken(Long userId);

    /**
     * Validates and rotates the submitted raw token.
     * - If the token hash is not found: throws InvalidTokenException(REFRESH_TOKEN_INVALID).
     * - If the token is already used (reuse): deletes ALL user tokens and
     *   throws SecurityBreachException.
     * - If the token is expired: deletes it and throws
     *   InvalidTokenException(REFRESH_TOKEN_EXPIRED).
     * - Otherwise: marks the old token used=true, issues a new token, and
     *   returns RefreshTokenResult with the new raw token and owning user.
     */
    RefreshTokenResult rotateRefreshToken(String rawToken);

    /**
     * Marks the token identified by rawToken as used (logout).
     * Silently does nothing if the token is not found.
     */
    void invalidateByRawToken(String rawToken);
}
