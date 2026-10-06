package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.FeedbackRequest;
import com.gmc.backend.dto.response.FeedbackResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Feedback;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.OrderStatus;
import com.gmc.backend.model.OrderType;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.ProductCategory;
import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.FeedbackRepository;
import com.gmc.backend.repository.OrderRepository;
import com.gmc.backend.repository.ProductRepository;
import com.gmc.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceImplTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private FeedbackServiceImpl feedbackService;

    private User user(Long id) {
        Role role = Role.builder().roleId(1L).roleName("CUSTOMER").build();
        return User.builder().userId(id).name("User" + id).email("user" + id + "@test.com")
                .password("pass").role(role).build();
    }

    private Order order(User owner) {
        return Order.builder().orderId(1L).user(owner).orderDate(LocalDateTime.now())
                .status(OrderStatus.DELIVERED).orderType(OrderType.B2C)
                .items(new ArrayList<>()).build();
    }

    @Test
    void submitFeedback_shouldThrowWhenOrderBelongsToDifferentUser() {
        User requestingUser = user(1L);
        User orderOwner = user(2L);
        Order order = order(orderOwner);

        when(userRepository.findById(1L)).thenReturn(Optional.of(requestingUser));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        FeedbackRequest req = new FeedbackRequest();
        req.setOrderId(1L);
        req.setRating(5);
        req.setComment("Great!");

        assertThrows(BusinessRuleException.class, () -> feedbackService.submitFeedback(1L, req));
    }

    @Test
    void submitFeedback_shouldThrowWhenAlreadySubmittedForOrder() {
        User u = user(1L);
        Order order = order(u);

        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(feedbackRepository.existsByUserAndOrder(u, order)).thenReturn(true);

        FeedbackRequest req = new FeedbackRequest();
        req.setOrderId(1L);
        req.setRating(4);
        req.setComment("Good!");

        assertThrows(BusinessRuleException.class, () -> feedbackService.submitFeedback(1L, req));
    }

    @Test
    void submitFeedback_shouldSaveAndReturnFeedback() {
        User u = user(1L);
        Order order = order(u);
        Feedback saved = Feedback.builder()
                .feedbackId(1L).rating(5).comment("Excellent!")
                .createdAt(LocalDateTime.now()).user(u).order(order).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(feedbackRepository.existsByUserAndOrder(u, order)).thenReturn(false);
        when(feedbackRepository.save(any())).thenReturn(saved);

        FeedbackRequest req = new FeedbackRequest();
        req.setOrderId(1L);
        req.setRating(5);
        req.setComment("Excellent!");

        FeedbackResponse result = feedbackService.submitFeedback(1L, req);

        assertEquals(5, result.getRating());
        assertEquals("Excellent!", result.getComment());
    }

    @Test
    void submitFeedback_shouldThrowWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        FeedbackRequest req = new FeedbackRequest();
        req.setOrderId(1L);
        req.setRating(3);
        req.setComment("Ok");

        assertThrows(ResourceNotFoundException.class, () -> feedbackService.submitFeedback(99L, req));
    }

    @Test
    void submitFeedback_shouldThrowWhenOrderNotFound() {
        User u = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        FeedbackRequest req = new FeedbackRequest();
        req.setOrderId(99L);
        req.setRating(3);
        req.setComment("Ok");

        assertThrows(ResourceNotFoundException.class, () -> feedbackService.submitFeedback(1L, req));
    }

    @Test
    void submitFeedback_shouldIncludeProductWhenProductIdProvided() {
        User u = user(1L);
        Order order = order(u);
        ProductCategory cat = ProductCategory.builder().categoryId(1L).name("Cat").build();
        Product product = Product.builder().productId(10L).name("Widget")
                .price(java.math.BigDecimal.TEN).category(cat).stockQuantity(5).build();
        Feedback saved = Feedback.builder()
                .feedbackId(2L).rating(4).comment("Good")
                .createdAt(LocalDateTime.now()).user(u).order(order).product(product).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(feedbackRepository.existsByUserAndOrder(u, order)).thenReturn(false);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(feedbackRepository.save(any())).thenReturn(saved);

        FeedbackRequest req = new FeedbackRequest();
        req.setOrderId(1L);
        req.setProductId(10L);
        req.setRating(4);
        req.setComment("Good");

        FeedbackResponse result = feedbackService.submitFeedback(1L, req);

        assertEquals(10L, result.getProductId());
    }

    @Test
    void getFeedbackByUser_shouldReturnUserFeedbackList() {
        User u = user(1L);
        Order order = order(u);
        Feedback feedback = Feedback.builder().feedbackId(1L).rating(4).comment("Good")
                .createdAt(LocalDateTime.now()).user(u).order(order).build();
        when(feedbackRepository.findByUser_UserId(1L)).thenReturn(List.of(feedback));

        List<FeedbackResponse> result = feedbackService.getFeedbackByUser(1L);

        assertEquals(1, result.size());
        assertEquals(4, result.get(0).getRating());
    }
}
