package com.gmc.backend.controller;

import com.gmc.backend.dto.request.ComplaintRequest;
import com.gmc.backend.dto.response.ComplaintResponse;
import com.gmc.backend.security.CustomUserDetails;
import com.gmc.backend.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    @PostMapping
    public ResponseEntity<ComplaintResponse> submit(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ComplaintRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complaintService.submitComplaint(user.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<ComplaintResponse>> getMyComplaints(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(complaintService.getComplaintsByUser(user.getId()));
    }
}
