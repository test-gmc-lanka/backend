package com.gmc.backend.service;

import com.gmc.backend.dto.request.MaintenanceAssignRequest;
import com.gmc.backend.dto.response.MaintenanceResponse;

import java.util.List;

public interface MaintenanceService {

    MaintenanceResponse assignMaintenance(MaintenanceAssignRequest request);

    List<MaintenanceResponse> getMaintenanceByTechnician(Long technicianId);

    List<MaintenanceResponse> getAllMaintenance();

    /** Technicians may only update their own tasks; status is ASSIGNED, IN_PROGRESS or COMPLETED. */
    MaintenanceResponse updateMaintenanceStatus(Long maintenanceId, Long technicianId, String status);
}
