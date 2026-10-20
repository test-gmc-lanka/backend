package com.gmc.backend.repository;

import com.gmc.backend.model.Cart;
import com.gmc.backend.model.CartItem;
import com.gmc.backend.model.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCart(Cart cart);

    List<CartItem> findByCart_CartId(Long cartId);

    Optional<CartItem> findByCartAndProduct(Cart cart, Product product);

    void deleteByCart(Cart cart);

    // Archived products are taken out of every cart
    @Modifying
    @Transactional
    void deleteByProduct(Product product);
}
