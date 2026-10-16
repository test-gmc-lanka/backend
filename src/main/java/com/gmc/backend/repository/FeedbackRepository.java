package com.gmc.backend.repository;

import com.gmc.backend.model.Feedback;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

     Page<Feedback> findByProduct_ProductId(Long productId, Pageable pageable);

     List<Feedback> findByUser(User user);

     List<Feedback> findByUser_UserId(Long userId);

     boolean existsByUserAndOrder(User user, Order order);

     boolean existsByUserAndOrderAndProduct(User user, Order order, Product product);

     List<Feedback> findByProduct_ProductIdOrderByCreatedAtDesc(Long productId);

     // Public reviews: hidden ones are excluded (NULL counts as visible for older rows)
     @Query("SELECT f FROM Feedback f WHERE f.product.productId = :productId "
             + "AND (f.hidden IS NULL OR f.hidden = false) ORDER BY f.createdAt DESC")
     List<Feedback> findVisibleByProduct(@Param("productId") Long productId);
}
