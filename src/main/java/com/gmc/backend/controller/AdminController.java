package com.gmc.backend.controller;

import com.gmc.backend.dto.request.*;
import com.gmc.backend.dto.response.*;
import com.gmc.backend.model.AppointmentStatus;
import com.gmc.backend.model.CustomDesignStatus;
import com.gmc.backend.model.OrderStatus;
import com.gmc.backend.service.FeedbackService;
import com.gmc.backend.service.OrderService;
import com.gmc.backend.service.UserService;
import com.gmc.backend.service.AppointmentService;
import com.gmc.backend.service.ComplaintService;
import com.gmc.backend.service.CustomDesignService;
import com.gmc.backend.service.MaintenanceService;
import com.gmc.backend.service.InvoiceService;
import com.gmc.backend.service.CategoryService;
import com.gmc.backend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final OrderService orderService;
    private final AppointmentService appointmentService;
    private final FeedbackService feedbackService;
    private final ComplaintService complaintService;
    private final CustomDesignService customDesignService;
    private final MaintenanceService maintenanceService;
    private final InvoiceService invoiceService;
    private final CategoryService categoryService;
    private final ProductService productService;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStatsResponse> getDashboard() {
        List<OrderResponse> allOrders = orderService.getAllOrders();
        long pending = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING).count();
        BigDecimal revenue = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .map(OrderResponse::getTotalAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        long appointmentsToday = appointmentService.getAllAppointments().stream()
                .filter(a -> !a.getDate().isBefore(startOfDay) && a.getDate().isBefore(endOfDay))
                .count();
        List<ProductResponse> lowStock = productService.getAllProducts().stream()
                .filter(p -> p.getStockQuantity() != null && p.getStockQuantity() <= 5)
                .toList();
        DashboardStatsResponse stats = DashboardStatsResponse.builder()
                .totalOrders(allOrders.size())
                .totalRevenue(revenue)
                .pendingOrders(pending)
                .appointmentsToday(appointmentsToday)
                .lowStockProducts(lowStock)
                .totalUsers(userService.getAllUsers().size())
                .totalProducts(productService.getAllProducts().size())
                .build();
        return ResponseEntity.ok(stats);
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getUsers(
            @RequestParam(required = false) String role) {
        if (role != null && !role.isBlank()) {
            return ResponseEntity.ok(userService.getUsersByRole(role));
        }
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PatchMapping("/users/{userId}/deactivate")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.deactivateUser(userId));
    }

    @PatchMapping("/users/{userId}/activate")
    public ResponseEntity<UserResponse> activateUser(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.activateUser(userId));
    }

    // ── Orders ────────────────────────────────────────────────────────────────

    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @RequestParam(required = false) OrderStatus status) {
        if (status != null) {
            return ResponseEntity.ok(orderService.getOrdersByStatus(status));
        }
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @PutMapping("/orders/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(orderId, request.getStatus()));
    }

    // ── Appointments ──────────────────────────────────────────────────────────

    @GetMapping("/appointments")
    public ResponseEntity<List<AppointmentResponse>> getAllAppointments(
            @RequestParam(required = false) AppointmentStatus status) {
        if (status != null) {
            return ResponseEntity.ok(appointmentService.getAppointmentsByStatus(status));
        }
        return ResponseEntity.ok(appointmentService.getAllAppointments());
    }

    @PutMapping("/appointments/{appointmentId}/status")
    public ResponseEntity<AppointmentResponse> updateAppointmentStatus(
            @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentStatusRequest request) {
        return ResponseEntity.ok(
                appointmentService.updateStatus(appointmentId, request.getStatus()));
    }

    // ── Feedback & Complaints ─────────────────────────────────────────────────

    @GetMapping("/feedback")
    public ResponseEntity<List<FeedbackResponse>> getAllFeedback() {
        return ResponseEntity.ok(feedbackService.getAllFeedback());
    }

    @GetMapping("/complaints")
    public ResponseEntity<List<ComplaintResponse>> getAllComplaints() {
        return ResponseEntity.ok(complaintService.getAllComplaints());
    }

    @PatchMapping("/complaints/{complaintId}/status")
    public ResponseEntity<ComplaintResponse> updateComplaintStatus(
            @PathVariable Long complaintId,
            @RequestParam String status) {
        return ResponseEntity.ok(complaintService.updateComplaintStatus(complaintId, status));
    }

    // ── Custom Designs ────────────────────────────────────────────────────────

    @GetMapping("/custom-designs")
    public ResponseEntity<List<CustomDesignResponse>> getAllDesigns() {
        return ResponseEntity.ok(customDesignService.getAllDesigns());
    }

    @PatchMapping("/custom-designs/{designId}/status")
    public ResponseEntity<CustomDesignResponse> updateDesignStatus(
            @PathVariable Long designId,
            @RequestParam CustomDesignStatus status) {
        return ResponseEntity.ok(customDesignService.updateStatus(designId, status));
    }

    // ── Billing / Invoices ────────────────────────────────────────────────────

    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceResponse>> getAllInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices());
    }

    @PostMapping("/invoices/orders/{orderId}")
    public ResponseEntity<InvoiceResponse> generateInvoice(@PathVariable Long orderId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invoiceService.generateInvoice(orderId));
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    @GetMapping("/maintenance")
    public ResponseEntity<List<MaintenanceResponse>> getAllMaintenance() {
        return ResponseEntity.ok(maintenanceService.getAllMaintenance());
    }

    @PostMapping("/maintenance")
    public ResponseEntity<MaintenanceResponse> assignMaintenance(
            @Valid @RequestBody MaintenanceAssignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(maintenanceService.assignMaintenance(request));
    }

    // ── Categories ────────────────────────────────────────────────────────────

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createCategory(request));
    }

    @PutMapping("/categories/{categoryId}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(categoryId, request));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }
}
