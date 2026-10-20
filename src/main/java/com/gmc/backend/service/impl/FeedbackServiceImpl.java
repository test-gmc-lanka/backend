package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.FeedbackRequest;
import com.gmc.backend.dto.request.FeedbackUpdateRequest;
import com.gmc.backend.dto.response.FeedbackResponse;
import com.gmc.backend.dto.response.ProductReviewsResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Feedback;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.OrderStatus;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.FeedbackRepository;
import com.gmc.backend.repository.OrderRepository;
import com.gmc.backend.repository.ProductRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.FeedbackService;
import com.gmc.backend.service.NotificationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    /**
     * Verified-purchase reviews: the customer must own the order, it must be delivered,
     * the product must be in it, and each product can be reviewed once per order.
     */
    @Override
    @Transactional
    public FeedbackResponse submitFeedback(Long userId, FeedbackRequest request) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Order order = orderRepository
                .findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderId()));
        if (!order.getUser().getUserId().equals(userId)) {
            throw new BusinessRuleException("Order does not belong to this user");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessRuleException("You can review products once your order has been delivered");
        }
        Product product = order.getItems().stream()
                .map(item -> item.getProduct())
                .filter(p -> p.getProductId().equals(request.getProductId()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("That product is not part of this order"));
        if (feedbackRepository.existsByUserAndOrderAndProduct(user, order, product)) {
            throw new BusinessRuleException("You have already reviewed this product");
        }
        Feedback saved = feedbackRepository.save(Feedback.builder()
                .rating(request.getRating())
                .comment(request.getComment().trim())
                .createdAt(LocalDateTime.now())
                .user(user)
                .order(order)
                .product(product)
                .build());
        alertIfLowRating(saved, "gave");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public FeedbackResponse updateFeedback(Long userId, Long feedbackId, FeedbackUpdateRequest request) {
        Feedback feedback = feedbackRepository
                .findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + feedbackId));
        if (!feedback.getUser().getUserId().equals(userId)) {
            throw new BusinessRuleException("You can only edit your own reviews");
        }
        String comment = request.getComment().trim();
        boolean changed = !feedback.getRating().equals(request.getRating())
                || !feedback.getComment().equals(comment);
        if (!changed) {
            return toResponse(feedback);
        }
        boolean newlyLow = request.getRating() <= 2 && feedback.getRating() > 2;
        feedback.setRating(request.getRating());
        feedback.setComment(comment);
        feedback.setUpdatedAt(LocalDateTime.now());
        Feedback saved = feedbackRepository.save(feedback);
        if (newlyLow) {
            alertIfLowRating(saved, "changed their review to");
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public FeedbackResponse setHidden(Long feedbackId, boolean hidden) {
        Feedback feedback = feedbackRepository
                .findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + feedbackId));
        feedback.setHidden(hidden);
        return toResponse(feedbackRepository.save(feedback));
    }

    // Low ratings deserve a quick follow-up from the team
    private void alertIfLowRating(Feedback f, String verb) {
        if (f.getRating() > 2) {
            return;
        }
        notificationService.notifyAdmins(
                f.getUser().getName() + " " + verb + " " + f.getProduct().getName() + " " + f.getRating() + "★ (order #"
                        + f.getOrder().getOrderId() + "): \"" + f.getComment() + "\"",
                "REVIEW",
                "/admin/feedback",
                null);
    }

    @Override
    public List<FeedbackResponse> getFeedbackByUser(Long userId) {
        return feedbackRepository.findByUser_UserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<FeedbackResponse> getAllFeedback() {
        return feedbackRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<FeedbackResponse> getFeedbackByProduct(Long productId) {
        return feedbackRepository.findByProduct_ProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProductReviewsResponse getProductReviews(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
        List<Feedback> reviews = feedbackRepository.findVisibleByProduct(productId);
        BigDecimal average = reviews.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(
                                reviews.stream().mapToInt(Feedback::getRating).sum())
                        .divide(BigDecimal.valueOf(reviews.size()), 1, RoundingMode.HALF_UP);
        return ProductReviewsResponse.builder()
                .averageRating(average)
                .reviewCount(reviews.size())
                .reviews(reviews.stream()
                        .map(f -> ProductReviewsResponse.Review.builder()
                                .reviewId(f.getFeedbackId())
                                .reviewerName(shortName(f.getUser().getName()))
                                .rating(f.getRating())
                                .comment(f.getComment())
                                .createdAt(f.getCreatedAt())
                                .edited(f.getUpdatedAt() != null)
                                .verifiedPurchase(f.getOrder() != null)
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    // "Kamal Perera" -> "Kamal P."; reviews are public, so full names aren't shown
    static String shortName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Customer";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private FeedbackResponse toResponse(Feedback f) {
        return FeedbackResponse.builder()
                .feedbackId(f.getFeedbackId())
                .orderId(f.getOrder().getOrderId())
                .productId(f.getProduct() != null ? f.getProduct().getProductId() : null)
                .productName(f.getProduct() != null ? f.getProduct().getName() : null)
                .productImageUrl(f.getProduct() != null ? f.getProduct().getImageUrl() : null)
                .rating(f.getRating())
                .comment(f.getComment())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .hidden(Boolean.TRUE.equals(f.getHidden()))
                .userId(f.getUser().getUserId())
                .userName(f.getUser().getName())
                .userEmail(f.getUser().getEmail())
                .build();
    }
}
