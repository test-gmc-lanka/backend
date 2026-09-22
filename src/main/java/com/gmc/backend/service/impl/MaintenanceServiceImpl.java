package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.MaintenanceAssignRequest;
import com.gmc.backend.dto.response.MaintenanceResponse;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Maintenance;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.MaintenanceRepository;
import com.gmc.backend.repository.ProductRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public MaintenanceResponse assignMaintenance(MaintenanceAssignRequest request) {
        User technician = userRepository.findById(request.getTechnicianId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Technician not found: " + request.getTechnicianId()));
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found: " + request.getProductId()));
        Maintenance maintenance = Maintenance.builder()
                .description(request.getDescription())
                .status("ASSIGNED")
                .cost(request.getCost())
                .user(technician)
                .product(product)
                .build();
        return toResponse(maintenanceRepository.save(maintenance));
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
    public MaintenanceResponse updateMaintenanceStatus(Long maintenanceId, String status) {
        Maintenance maintenance = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Maintenance task not found: " + maintenanceId));
        maintenance.setStatus(status);
        return toResponse(maintenanceRepository.save(maintenance));
    }

    private MaintenanceResponse toResponse(Maintenance m) {
        return MaintenanceResponse.builder()
                .maintenanceId(m.getMaintenanceId())
                .description(m.getDescription())
                .status(m.getStatus())
                .cost(m.getCost())
                .productId(m.getProduct().getProductId())
                .productName(m.getProduct().getName())
                .technicianId(m.getUser().getUserId())
                .technicianName(m.getUser().getName())
                .build();
    }
}
