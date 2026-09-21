package com.gmc.backend.controller;

import com.gmc.backend.dto.request.FeedbackRequest;
import com.gmc.backend.dto.response.FeedbackResponse;
import com.gmc.backend.security.CustomUserDetails;
import com.gmc.backend.service.FeedbackService;
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

    @GetMapping
    public ResponseEntity<List<FeedbackResponse>> getMyFeedback(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(feedbackService.getFeedbackByUser(user.getId()));
    }
}
