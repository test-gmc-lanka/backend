package com.gmc.backend.service;

import com.gmc.backend.dto.request.MaintenanceAssignRequest;
import com.gmc.backend.dto.response.MaintenanceResponse;

import java.util.List;

public interface MaintenanceService {

    MaintenanceResponse assignMaintenance(MaintenanceAssignRequest request);

    List<MaintenanceResponse> getMaintenanceByTechnician(Long technicianId);

    List<MaintenanceResponse> getAllMaintenance();

    MaintenanceResponse updateMaintenanceStatus(Long maintenanceId, String status);
}
