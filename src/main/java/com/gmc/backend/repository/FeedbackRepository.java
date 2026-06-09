package com.gmc.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.gmc.backend.model.Feedback;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
     Page<Feedback> findByProduct_ProductId(Long productId, Pageable pageable);
}
