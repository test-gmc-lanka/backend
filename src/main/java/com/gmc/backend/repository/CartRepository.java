package com.gmc.backend.repository;

import com.gmc.backend.model.Cart;
import com.gmc.backend.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUser(User user);

    Optional<Cart> findByUser_UserId(Long userId);

    boolean existsByUser(User user);
}
