package com.gmc.backend.controller;

import com.gmc.backend.dto.request.FeedbackRequest;
import com.gmc.backend.dto.request.FeedbackUpdateRequest;
import com.gmc.backend.dto.response.FeedbackResponse;
import com.gmc.backend.security.CustomUserDetails;
import com.gmc.backend.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<FeedbackResponse> submit(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody FeedbackRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(feedbackService.submitFeedback(user.getId(), request));
    }

    @PutMapping("/{feedbackId}")
    public ResponseEntity<FeedbackResponse> update(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackUpdateRequest request) {
        return ResponseEntity.ok(feedbackService.updateFeedback(user.getId(), feedbackId, request));
    }

    @GetMapping
    public ResponseEntity<List<FeedbackResponse>> getMyFeedback(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(feedbackService.getFeedbackByUser(user.getId()));
    }
}
