package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.ComplaintRequest;
import com.gmc.backend.dto.response.ComplaintResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.model.Complaint;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.OrderItem;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.ComplaintRepository;
import com.gmc.backend.repository.OrderRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.EmailService;
import com.gmc.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceImplTest {

    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private ComplaintServiceImpl complaintService;

    private User customer() {
        return User.builder().userId(1L).name("Alice").email("alice@example.com").build();
    }

    private Order orderWithChair(User owner) {
        Order order = Order.builder().orderId(12L).user(owner).items(new ArrayList<>()).build();
        Product chair = Product.builder().productId(3L).name("Teak Chair").build();
        order.getItems().add(OrderItem.builder().order(order).product(chair).quantity(1).build());
        return order;
    }

    private ComplaintRequest request(Long orderId, Long productId) {
        ComplaintRequest req = new ComplaintRequest();
        req.setDescription("  Leg is cracked ");
        req.setOrderId(orderId);
        req.setProductId(productId);
        return req;
    }

    @Test
    void submitComplaint_aboutProductInOwnOrder_savesAndNotifiesAdminsByEmail() {
        User alice = customer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(orderRepository.findById(12L)).thenReturn(Optional.of(orderWithChair(alice)));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplaintResponse result = complaintService.submitComplaint(1L, request(12L, 3L));

        assertThat(result.getDescription()).isEqualTo("Leg is cracked");
        assertThat(result.getStatus()).isEqualTo("OPEN");
        assertThat(result.getOrderId()).isEqualTo(12L);
        assertThat(result.getProductName()).isEqualTo("Teak Chair");
        verify(notificationService).notifyAdmins(contains("Teak Chair (order #12)"), eq("COMPLAINT"),
                eq("/admin/feedback"), eq("New customer complaint"));
        verify(notificationService).sendNotification(eq(1L), anyString(), eq("COMPLAINT"), eq("/orders/12"));
    }

    @Test
    void submitComplaint_withoutOrder_isAllowed() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplaintResponse result = complaintService.submitComplaint(1L, request(null, null));

        assertThat(result.getOrderId()).isNull();
        verify(notificationService).notifyAdmins(anyString(), eq("COMPLAINT"), anyString(), anyString());
    }

    @Test
    void submitComplaint_aboutSomeoneElsesOrder_isRejected() {
        User bob = User.builder().userId(2L).name("Bob").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(orderRepository.findById(12L)).thenReturn(Optional.of(orderWithChair(bob)));

        assertThatThrownBy(() -> complaintService.submitComplaint(1L, request(12L, 3L)))
                .isInstanceOf(BusinessRuleException.class);
        verify(complaintRepository, never()).save(any());
    }

    @Test
    void submitComplaint_productNotInOrder_isRejected() {
        User alice = customer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(orderRepository.findById(12L)).thenReturn(Optional.of(orderWithChair(alice)));

        assertThatThrownBy(() -> complaintService.submitComplaint(1L, request(12L, 99L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void updateComplaintStatus_resolved_notifiesCustomerInAppAndByEmail() {
        User alice = customer();
        Complaint complaint = Complaint.builder().complaintId(5L).description("Leg is cracked")
                .date(LocalDateTime.now()).status("OPEN").user(alice).order(orderWithChair(alice)).build();
        when(complaintRepository.findById(5L)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplaintResponse result = complaintService.updateComplaintStatus(5L, "resolved");

        assertThat(result.getStatus()).isEqualTo("RESOLVED");
        verify(notificationService).sendNotification(eq(1L), contains("resolved"), eq("COMPLAINT"), eq("/orders/12"));
        verify(emailService).send(eq("alice@example.com"), contains("resolved"), contains("Leg is cracked"));
    }

    @Test
    void updateComplaintStatus_invalidStatus_isRejected() {
        Complaint complaint = Complaint.builder().complaintId(5L).status("OPEN").user(customer()).build();
        when(complaintRepository.findById(5L)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> complaintService.updateComplaintStatus(5L, "DELETED"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void getComplaintsByUser_mapsResults() {
        Complaint complaint = Complaint.builder().complaintId(5L).description("d").status("OPEN")
                .date(LocalDateTime.now()).user(customer()).build();
        when(complaintRepository.findByUser_UserId(1L)).thenReturn(List.of(complaint));

        assertThat(complaintService.getComplaintsByUser(1L)).hasSize(1);
    }
}
