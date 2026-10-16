package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.MaintenanceAssignRequest;
import com.gmc.backend.dto.response.MaintenanceResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Maintenance;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.MaintenanceRepository;
import com.gmc.backend.repository.ProductRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.EmailService;
import com.gmc.backend.service.MaintenanceService;
import com.gmc.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    private static final Set<String> TASK_STATUSES = Set.of("ASSIGNED", "IN_PROGRESS", "COMPLETED");
    private static final DateTimeFormatter DEADLINE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);

    @Override
    @Transactional
    public MaintenanceResponse assignMaintenance(MaintenanceAssignRequest request) {
        User technician = userRepository.findById(request.getTechnicianId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Technician not found: " + request.getTechnicianId()));
        if (!"TECHNICIAN".equals(technician.getRole() == null ? null : technician.getRole().getRoleName())
                || Boolean.FALSE.equals(technician.getActive())) {
            throw new BusinessRuleException("Tasks can only be assigned to an active technician");
        }
        String title = request.getTitle() == null ? null : request.getTitle().trim();
        if (request.getProductId() == null && (title == null || title.isEmpty())) {
            throw new BusinessRuleException("Select a product or enter a custom design name");
        }
        Product product = null;
        if (request.getProductId() != null) {
            product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found: " + request.getProductId()));
        }
        List<String> images = request.getImageUrls() == null
                ? new ArrayList<>()
                : request.getImageUrls().stream()
                        .filter(Objects::nonNull)
                        .filter(url -> !url.isBlank())
                        .distinct()
                        .collect(Collectors.toCollection(ArrayList::new));
        Maintenance maintenance = Maintenance.builder()
                .description(request.getDescription())
                .status("ASSIGNED")
                .cost(request.getCost())
                .user(technician)
                .product(product)
                .title(product == null ? title : null)
                .imageUrls(images)
                .deadline(request.getDeadline())
                .build();
        if (request.getDeadline() != null && request.getDeadline().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("Deadline can't be in the past");
        }
        Maintenance saved = maintenanceRepository.save(maintenance);
        notifyTechnician(saved);
        return toResponse(saved);
    }

    @Override
    public List<MaintenanceResponse> getMaintenanceByTechnician(Long technicianId) {
        return maintenanceRepository.findByUser_UserId(technicianId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MaintenanceResponse> getAllMaintenance() {
        return maintenanceRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MaintenanceResponse updateMaintenanceStatus(Long maintenanceId, Long technicianId, String status) {
        Maintenance maintenance = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Maintenance task not found: " + maintenanceId));
        if (!maintenance.getUser().getUserId().equals(technicianId)) {
            throw new BusinessRuleException("This task is not assigned to you");
        }
        String newStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!TASK_STATUSES.contains(newStatus)) {
            throw new BusinessRuleException("Status must be one of " + TASK_STATUSES);
        }
        if ("COMPLETED".equals(maintenance.getStatus())) {
            throw new BusinessRuleException("This task is already completed");
        }
        maintenance.setStatus(newStatus);
        Maintenance saved = maintenanceRepository.save(maintenance);
        if ("COMPLETED".equals(newStatus)) {
            notificationService.notifyAdmins(saved.getUser().getName() + " completed maintenance task #"
                    + saved.getMaintenanceId() + " (" + subjectOf(saved) + ").",
                    "MAINTENANCE", "/admin/maintenance", null);
        }
        return toResponse(saved);
    }

    // Technicians may not be logged in when a job is assigned, so they also get an email
    private void notifyTechnician(Maintenance task) {
        User technician = task.getUser();
        String due = task.getDeadline() == null ? "" : " Due " + task.getDeadline().format(DEADLINE_FORMAT) + ".";
        String message = "New task #" + task.getMaintenanceId() + " assigned to you: " + subjectOf(task)
                + "." + due + " " + task.getDescription();
        notificationService.sendNotification(technician.getUserId(), message, "MAINTENANCE", "/technician/tasks");
        emailService.send(technician.getEmail(), "GMC Lanka – New maintenance task #" + task.getMaintenanceId(),
                "Hi " + technician.getName() + ",\n\nYou have a new maintenance task.\n\n"
                        + "Task: " + subjectOf(task) + "\n"
                        + "Details: " + task.getDescription() + "\n"
                        + (task.getDeadline() == null ? ""
                                : "Deadline: " + task.getDeadline().format(DEADLINE_FORMAT) + "\n")
                        + (task.getImageUrls().isEmpty() ? ""
                                : "Reference images: " + task.getImageUrls().size() + " (see the task in the app)\n")
                        + "\nOpen My Tasks in the GMC Lanka app to get started.\n\n— GMC Lanka");
    }

    private static String subjectOf(Maintenance task) {
        return task.getProduct() != null ? task.getProduct().getName() : "custom design \"" + task.getTitle() + "\"";
    }

    private MaintenanceResponse toResponse(Maintenance m) {
        return MaintenanceResponse.builder()
                .maintenanceId(m.getMaintenanceId())
                .description(m.getDescription())
                .status(m.getStatus())
                .cost(m.getCost())
                .productId(m.getProduct() == null ? null : m.getProduct().getProductId())
                .productName(m.getProduct() == null ? null : m.getProduct().getName())
                .title(m.getTitle())
                .imageUrls(m.getImageUrls() == null ? List.of() : new ArrayList<>(m.getImageUrls()))
                .technicianId(m.getUser().getUserId())
                .technicianName(m.getUser().getName())
                .deadline(m.getDeadline())
                .build();
    }
}
