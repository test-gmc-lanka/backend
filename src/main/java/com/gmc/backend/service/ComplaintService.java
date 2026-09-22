package com.gmc.backend.service;

import com.gmc.backend.dto.request.ComplaintRequest;
import com.gmc.backend.dto.response.ComplaintResponse;

import java.util.List;

public interface ComplaintService {

    ComplaintResponse submitComplaint(Long userId, ComplaintRequest request);

    List<ComplaintResponse> getComplaintsByUser(Long userId);

    List<ComplaintResponse> getAllComplaints();

    ComplaintResponse updateComplaintStatus(Long complaintId, String status);
}
