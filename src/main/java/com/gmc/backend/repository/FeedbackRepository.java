package com.gmc.backend.repository;

import com.gmc.backend.model.Feedback;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

     Page<Feedback> findByProduct_ProductId(Long productId, Pageable pageable);

     List<Feedback> findByUser(User user);

     List<Feedback> findByUser_UserId(Long userId);

     boolean existsByUserAndOrder(User user, Order order);
}
