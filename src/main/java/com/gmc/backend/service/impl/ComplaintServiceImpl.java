package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.ComplaintRequest;
import com.gmc.backend.dto.response.ComplaintResponse;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Complaint;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.ComplaintRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;

    @Override
    public ComplaintResponse submitComplaint(Long userId, ComplaintRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Complaint complaint = Complaint.builder()
                .description(request.getDescription())
                .date(LocalDateTime.now())
                .status("OPEN")
                .user(user)
                .build();
        return toResponse(complaintRepository.save(complaint));
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
    public ComplaintResponse updateComplaintStatus(Long complaintId, String status) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Complaint not found: " + complaintId));
        complaint.setStatus(status);
        return toResponse(complaintRepository.save(complaint));
    }

    private ComplaintResponse toResponse(Complaint c) {
        return ComplaintResponse.builder()
                .complaintId(c.getComplaintId())
                .description(c.getDescription())
                .date(c.getDate())
                .status(c.getStatus())
                .userId(c.getUser().getUserId())
                .userName(c.getUser().getName())
                .build();
    }
}
