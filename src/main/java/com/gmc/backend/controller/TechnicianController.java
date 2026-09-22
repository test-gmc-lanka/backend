package com.gmc.backend.controller;

import com.gmc.backend.dto.response.MaintenanceResponse;
import com.gmc.backend.security.CustomUserDetails;
import com.gmc.backend.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/technician")
@RequiredArgsConstructor
public class TechnicianController {

    private final MaintenanceService maintenanceService;

    @GetMapping("/tasks")
    public ResponseEntity<List<MaintenanceResponse>> getMyTasks(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(maintenanceService.getMaintenanceByTechnician(user.getId()));
    }

    @PatchMapping("/tasks/{maintenanceId}/status")
    public ResponseEntity<MaintenanceResponse> updateTaskStatus(
            @PathVariable Long maintenanceId,
            @RequestParam String status) {
        return ResponseEntity.ok(
                maintenanceService.updateMaintenanceStatus(maintenanceId, status));
    }
}
