package com.gmc.backend.controller;

import com.gmc.backend.dto.request.AppointmentRequest;
import com.gmc.backend.dto.request.RescheduleRequest;
import com.gmc.backend.dto.response.AppointmentResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.security.CustomUserDetails;
import com.gmc.backend.service.AppointmentService;
import com.gmc.backend.service.FileStorageService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final FileStorageService fileStorageService;

    // Upload photos first, then send the returned URLs with the booking
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<String>> uploadImages(@RequestPart("files") List<MultipartFile> files) {
        if (files.size() > 6) {
            throw new BusinessRuleException("You can attach up to 6 images");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(fileStorageService.storeImages(files, "appointments"));
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> book(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody AppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appointmentService.bookAppointment(user.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> getMyAppointments(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByUser(user.getId()));
    }

    @PutMapping("/{appointmentId}/reschedule")
    public ResponseEntity<AppointmentResponse> reschedule(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long appointmentId,
            @Valid @RequestBody RescheduleRequest request) {
        return ResponseEntity.ok(appointmentService.reschedule(appointmentId, user.getId(), request));
    }

    @DeleteMapping("/{appointmentId}")
    public ResponseEntity<AppointmentResponse> cancel(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long appointmentId) {
        return ResponseEntity.ok(appointmentService.cancelAppointment(appointmentId, user.getId()));
    }
}
