package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.FeedbackRequest;
import com.gmc.backend.dto.request.FeedbackUpdateRequest;
import com.gmc.backend.dto.response.FeedbackResponse;
import com.gmc.backend.dto.response.ProductReviewsResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Feedback;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.OrderItem;
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
import com.gmc.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private FeedbackServiceImpl feedbackService;

    private User user(Long id) {
        Role role = Role.builder().roleId(1L).roleName("CUSTOMER").build();
        return User.builder().userId(id).name("Kamal Perera").email("user" + id + "@test.com")
                .password("pass").role(role).build();
    }

    private Product chair() {
        return Product.builder().productId(10L).name("Teak Chair").price(BigDecimal.TEN)
                .category(ProductCategory.builder().categoryId(1L).name("Furniture").build()).build();
    }

    // Delivered order containing the chair
    private Order order(User owner, OrderStatus status) {
        Order order = Order.builder().orderId(1L).user(owner).orderDate(LocalDateTime.now())
                .status(status).orderType(OrderType.B2C).items(new ArrayList<>()).build();
        order.getItems().add(OrderItem.builder().order(order).product(chair()).quantity(1)
                .unitPrice(BigDecimal.TEN).build());
        return order;
    }

    private FeedbackRequest request(int rating) {
        FeedbackRequest req = new FeedbackRequest();
        req.setOrderId(1L);
        req.setProductId(10L);
        req.setRating(rating);
        req.setComment("  Solid and well finished  ");
        return req;
    }

    @Test
    void submitFeedback_shouldSaveReviewForDeliveredProduct() {
        User u = user(1L);
        Order order = order(u, OrderStatus.DELIVERED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(feedbackRepository.existsByUserAndOrderAndProduct(eq(u), eq(order), any(Product.class))).thenReturn(false);
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(inv -> inv.getArgument(0));

        FeedbackResponse result = feedbackService.submitFeedback(1L, request(5));

        assertEquals(5, result.getRating());
        assertEquals("Solid and well finished", result.getComment());
        assertEquals(10L, result.getProductId());
        assertEquals("Teak Chair", result.getProductName());
        verify(notificationService, never()).notifyAdmins(anyString(), anyString(), anyString(), any());
    }

    @Test
    void submitFeedback_lowRatingShouldAlertAdmins() {
        User u = user(1L);
        Order order = order(u, OrderStatus.DELIVERED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(inv -> inv.getArgument(0));

        feedbackService.submitFeedback(1L, request(2));

        verify(notificationService).notifyAdmins(contains("Teak Chair 2★"), eq("REVIEW"),
                eq("/admin/feedback"), isNull());
    }

    @Test
    void submitFeedback_shouldRejectBeforeDelivery() {
        User u = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order(u, OrderStatus.SHIPPED)));

        assertThrows(BusinessRuleException.class, () -> feedbackService.submitFeedback(1L, request(5)));
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void submitFeedback_shouldRejectProductNotInOrder() {
        User u = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order(u, OrderStatus.DELIVERED)));
        FeedbackRequest req = request(5);
        req.setProductId(99L);

        assertThrows(BusinessRuleException.class, () -> feedbackService.submitFeedback(1L, req));
    }

    @Test
    void submitFeedback_shouldThrowWhenOrderBelongsToDifferentUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order(user(2L), OrderStatus.DELIVERED)));

        assertThrows(BusinessRuleException.class, () -> feedbackService.submitFeedback(1L, request(5)));
    }

    @Test
    void submitFeedback_shouldThrowWhenProductAlreadyReviewed() {
        User u = user(1L);
        Order order = order(u, OrderStatus.DELIVERED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(feedbackRepository.existsByUserAndOrderAndProduct(eq(u), eq(order), any(Product.class))).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> feedbackService.submitFeedback(1L, request(5)));
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void submitFeedback_shouldThrowWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> feedbackService.submitFeedback(99L, request(5)));
    }

    @Test
    void submitFeedback_shouldThrowWhenOrderNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> feedbackService.submitFeedback(1L, request(5)));
    }

    @Test
    void getProductReviews_shouldAverageAndShortenNames() {
        User u = user(1L);
        Order order = order(u, OrderStatus.DELIVERED);
        Feedback five = Feedback.builder().feedbackId(1L).rating(5).comment("Great").user(u).order(order)
                .product(chair()).createdAt(LocalDateTime.now()).build();
        Feedback four = Feedback.builder().feedbackId(2L).rating(4).comment("Good").user(u).order(order)
                .product(chair()).createdAt(LocalDateTime.now()).build();
        when(productRepository.existsById(10L)).thenReturn(true);
        when(feedbackRepository.findVisibleByProduct(10L)).thenReturn(List.of(five, four));

        ProductReviewsResponse result = feedbackService.getProductReviews(10L);

        assertEquals(new BigDecimal("4.5"), result.getAverageRating());
        assertEquals(2, result.getReviewCount());
        assertEquals("Kamal P.", result.getReviews().get(0).getReviewerName());
        assertTrue(result.getReviews().get(0).isVerifiedPurchase());
    }

    @Test
    void getProductReviews_noReviews_returnsZeroAverage() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(feedbackRepository.findVisibleByProduct(10L)).thenReturn(List.of());

        ProductReviewsResponse result = feedbackService.getProductReviews(10L);

        assertEquals(0, result.getReviewCount());
        assertEquals(BigDecimal.ZERO, result.getAverageRating());
    }

    @Test
    void shortName_handlesSingleAndMultipleNames() {
        assertEquals("Nimal", FeedbackServiceImpl.shortName("Nimal"));
        assertEquals("Ayesha R.", FeedbackServiceImpl.shortName("Ayesha Kumari Rathnayake"));
        assertEquals("Customer", FeedbackServiceImpl.shortName(" "));
    }

    @Test
    void getFeedbackByUser_shouldReturnUserFeedbackList() {
        User u = user(1L);
        Feedback f = Feedback.builder().feedbackId(1L).rating(5).comment("ok").user(u)
                .order(order(u, OrderStatus.DELIVERED)).createdAt(LocalDateTime.now()).build();
        when(feedbackRepository.findByUser_UserId(1L)).thenReturn(List.of(f));

        assertEquals(1, feedbackService.getFeedbackByUser(1L).size());
    }

    // ════════════════════════ editing & moderation ═══════════════════════════

    private Feedback existingReview(User owner, int rating) {
        return Feedback.builder().feedbackId(7L).rating(rating).comment("Good").user(owner)
                .order(order(owner, OrderStatus.DELIVERED)).product(chair()).createdAt(LocalDateTime.now()).build();
    }

    private FeedbackUpdateRequest update(int rating, String comment) {
        FeedbackUpdateRequest req = new FeedbackUpdateRequest();
        req.setRating(rating);
        req.setComment(comment);
        return req;
    }

    @Test
    void updateFeedback_ownerCanEdit_andItIsMarkedEdited() {
        Feedback review = existingReview(user(1L), 4);
        when(feedbackRepository.findById(7L)).thenReturn(Optional.of(review));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(inv -> inv.getArgument(0));

        FeedbackResponse result = feedbackService.updateFeedback(1L, 7L, update(5, " Even better after a month "));

        assertEquals(5, result.getRating());
        assertEquals("Even better after a month", result.getComment());
        assertNotNull(result.getUpdatedAt());
        verify(notificationService, never()).notifyAdmins(anyString(), anyString(), anyString(), any());
    }

    @Test
    void updateFeedback_droppingToLowRatingAlertsAdmins() {
        Feedback review = existingReview(user(1L), 4);
        when(feedbackRepository.findById(7L)).thenReturn(Optional.of(review));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(inv -> inv.getArgument(0));

        feedbackService.updateFeedback(1L, 7L, update(1, "Broke after two weeks"));

        verify(notificationService).notifyAdmins(contains("changed their review to Teak Chair 1★"), eq("REVIEW"),
                eq("/admin/feedback"), isNull());
    }

    @Test
    void updateFeedback_otherUserIsRejected() {
        when(feedbackRepository.findById(7L)).thenReturn(Optional.of(existingReview(user(2L), 4)));

        assertThrows(BusinessRuleException.class,
                () -> feedbackService.updateFeedback(1L, 7L, update(1, "Not mine")));
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void updateFeedback_unchangedReviewIsNotMarkedEdited() {
        when(feedbackRepository.findById(7L)).thenReturn(Optional.of(existingReview(user(1L), 4)));

        FeedbackResponse result = feedbackService.updateFeedback(1L, 7L, update(4, "Good"));

        assertEquals(null, result.getUpdatedAt());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void setHidden_togglesVisibility() {
        Feedback review = existingReview(user(1L), 1);
        when(feedbackRepository.findById(7L)).thenReturn(Optional.of(review));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(feedbackService.setHidden(7L, true).isHidden());
        assertFalse(feedbackService.setHidden(7L, false).isHidden());
    }

    @Test
    void adminView_includesProductNameImageAndCustomerEmail() {
        User u = user(1L);
        Feedback review = existingReview(u, 5);
        when(feedbackRepository.findAll()).thenReturn(List.of(review));

        FeedbackResponse r = feedbackService.getAllFeedback().get(0);

        assertEquals("Teak Chair", r.getProductName());
        assertEquals("user1@test.com", r.getUserEmail());
        assertEquals(1L, r.getOrderId());
        assertFalse(r.isHidden());
    }
}
