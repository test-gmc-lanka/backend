package com.gmc.backend.repository;

import com.gmc.backend.model.RefreshToken;
import com.gmc.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String hashedToken);

    List<RefreshToken> findAllByUser(User user);

    @Modifying
    @Transactional
    void deleteAllByUser(User user);
}
