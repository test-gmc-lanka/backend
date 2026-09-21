package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.FeedbackRequest;
import com.gmc.backend.dto.response.FeedbackResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Feedback;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.FeedbackRepository;
import com.gmc.backend.repository.OrderRepository;
import com.gmc.backend.repository.ProductRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Override
    public FeedbackResponse submitFeedback(Long userId, FeedbackRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + request.getOrderId()));
        if (!order.getUser().getUserId().equals(userId)) {
            throw new BusinessRuleException("Order does not belong to this user");
        }
        if (feedbackRepository.existsByUserAndOrder(user, order)) {
            throw new BusinessRuleException("Feedback already submitted for this order");
        }
        Product product = null;
        if (request.getProductId() != null) {
            product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found: " + request.getProductId()));
        }
        Feedback feedback = Feedback.builder()
                .rating(request.getRating())
                .comment(request.getComment())
                .createdAt(LocalDateTime.now())
                .user(user)
                .order(order)
                .product(product)
                .build();
        return toResponse(feedbackRepository.save(feedback));
    }

    @Override
    public List<FeedbackResponse> getFeedbackByUser(Long userId) {
        return feedbackRepository.findByUser_UserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<FeedbackResponse> getAllFeedback() {
        return feedbackRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<FeedbackResponse> getFeedbackByProduct(Long productId) {
        return feedbackRepository.findByProduct_ProductId(productId, Pageable.unpaged())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private FeedbackResponse toResponse(Feedback f) {
        return FeedbackResponse.builder()
                .feedbackId(f.getFeedbackId())
                .orderId(f.getOrder().getOrderId())
                .productId(f.getProduct() != null ? f.getProduct().getProductId() : null)
                .rating(f.getRating())
                .comment(f.getComment())
                .createdAt(f.getCreatedAt())
                .userId(f.getUser().getUserId())
                .userName(f.getUser().getName())
                .build();
    }
}
