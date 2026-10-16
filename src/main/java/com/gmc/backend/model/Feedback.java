package com.gmc.backend.model;

import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedbacks",
        uniqueConstraints = @UniqueConstraint(name = "uk_feedback_user_order_product",
                columnNames = {"user_id", "order_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long feedbackId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    @Column(nullable = false)
    private Integer rating;

    @NotBlank(message = "Comment is required")
    @Column(length = 1000, nullable = false)
    private String comment;

    @NotNull(message = "Created date is required")
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Set when the customer edits their review
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Hidden by an admin (abusive, spam, off-topic); hidden reviews are not shown publicly.
    // Nullable so rows created before moderation existed count as visible.
    @Column(name = "hidden")
    private Boolean hidden;

    @NotNull(message = "User is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull(message = "Order is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;
}
