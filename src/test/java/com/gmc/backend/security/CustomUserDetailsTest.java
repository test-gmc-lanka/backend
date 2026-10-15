package com.gmc.backend.security;

import com.gmc.backend.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomUserDetailsTest {

    @Test
    void isEnabled_activeUser_returnsTrue() {
        User user = User.builder().active(true).build();

        assertTrue(new CustomUserDetails(user).isEnabled());
    }

    @Test
    void isEnabled_deactivatedUser_returnsFalse() {
        User user = User.builder().active(false).build();

        assertFalse(new CustomUserDetails(user).isEnabled());
    }

    @Test
    void isEnabled_nullActive_treatedAsActive() {
        User user = User.builder().build();
        user.setActive(null);

        assertTrue(new CustomUserDetails(user).isEnabled());
    }
}
