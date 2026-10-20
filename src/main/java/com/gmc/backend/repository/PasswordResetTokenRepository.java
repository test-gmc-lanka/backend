package com.gmc.backend.repository;

import com.gmc.backend.model.PasswordResetToken;
import com.gmc.backend.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String hashedToken);

    @Modifying
    @Transactional
    void deleteAllByUser(User user);
}
