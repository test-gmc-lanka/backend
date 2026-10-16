package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.ComplaintRequest;
import com.gmc.backend.dto.response.ComplaintResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Complaint;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.ComplaintRepository;
import com.gmc.backend.repository.OrderRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.ComplaintService;
import com.gmc.backend.service.EmailService;
import com.gmc.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED");
    private static final String ADMIN_COMPLAINTS_LINK = "/admin/feedback";

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Override
    @Transactional
    public ComplaintResponse submitComplaint(Long userId, ComplaintRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Order order = null;
        Product product = null;
        if (request.getOrderId() != null) {
            order = orderRepository.findById(request.getOrderId())
                    .filter(o -> o.getUser().getUserId().equals(userId))
                    .orElseThrow(() -> new BusinessRuleException("You can only report problems with your own orders"));
            if (request.getProductId() != null) {
                product = order.getItems().stream()
                        .map(item -> item.getProduct())
                        .filter(p -> p.getProductId().equals(request.getProductId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessRuleException("That product is not part of this order"));
            }
        }
        Complaint saved = complaintRepository.save(Complaint.builder()
                .description(request.getDescription().trim())
                .date(LocalDateTime.now())
                .status("OPEN")
                .user(user)
                .order(order)
                .product(product)
                .build());

        String about = describe(saved);
        notificationService.sendNotification(userId, "We received your complaint" + about
                + ". Our team will look into it and get back to you.", "COMPLAINT", "/orders"
                + (order == null ? "" : "/" + order.getOrderId()));
        // Complaints need prompt attention, so admins are emailed as well
        notificationService.notifyAdmins("New complaint from " + user.getName() + about + ": \""
                + saved.getDescription() + "\"", "COMPLAINT", ADMIN_COMPLAINTS_LINK, "New customer complaint");
        return toResponse(saved);
    }

    @Override
    public List<ComplaintResponse> getComplaintsByUser(Long userId) {
        return complaintRepository.findByUser_UserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ComplaintResponse> getAllComplaints() {
        return complaintRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ComplaintResponse updateComplaintStatus(Long complaintId, String status) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Complaint not found: " + complaintId));
        String newStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(newStatus)) {
            throw new BusinessRuleException("Status must be one of " + STATUSES);
        }
        complaint.setStatus(newStatus);
        Complaint saved = complaintRepository.save(complaint);

        User user = saved.getUser();
        String link = saved.getOrder() == null ? "/orders" : "/orders/" + saved.getOrder().getOrderId();
        if ("RESOLVED".equals(newStatus)) {
            String message = "Your complaint" + describe(saved) + " has been resolved. "
                    + "Thank you for your patience.";
            notificationService.sendNotification(user.getUserId(), message, "COMPLAINT", link);
            emailService.send(user.getEmail(), "GMC Lanka – Your complaint has been resolved",
                    "Hi " + user.getName() + ",\n\n" + message + "\n\nYour complaint: \""
                            + saved.getDescription() + "\"\n\nIf the problem continues, just reply "
                            + "through the app and we'll help.\n\n— GMC Lanka");
        } else if ("IN_PROGRESS".equals(newStatus)) {
            notificationService.sendNotification(user.getUserId(), "We're working on your complaint"
                    + describe(saved) + ".", "COMPLAINT", link);
        }
        return toResponse(saved);
    }

    // e.g. " about Teak Chair (order #12)"
    private static String describe(Complaint c) {
        if (c.getProduct() != null && c.getOrder() != null) {
            return " about " + c.getProduct().getName() + " (order #" + c.getOrder().getOrderId() + ")";
        }
        if (c.getOrder() != null) {
            return " about order #" + c.getOrder().getOrderId();
        }
        return "";
    }

    private ComplaintResponse toResponse(Complaint c) {
        return ComplaintResponse.builder()
                .complaintId(c.getComplaintId())
                .description(c.getDescription())
                .date(c.getDate())
                .status(c.getStatus())
                .userId(c.getUser().getUserId())
                .userName(c.getUser().getName())
                .orderId(c.getOrder() == null ? null : c.getOrder().getOrderId())
                .productId(c.getProduct() == null ? null : c.getProduct().getProductId())
                .productName(c.getProduct() == null ? null : c.getProduct().getName())
                .build();
    }
}
