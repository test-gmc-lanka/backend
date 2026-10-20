package com.gmc.backend.controller;

import com.gmc.backend.dto.request.ContactRequest;
import com.gmc.backend.security.RateLimiter;
import com.gmc.backend.service.ContactService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class ContactController {

    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final ContactService contactService;
    private final RateLimiter rateLimiter;

    // Public: anyone can write to the shop. Limited so the form can't be used to flood admins.
    @PostMapping
    public ResponseEntity<Map<String, String>> send(
            @Valid @RequestBody ContactRequest request, HttpServletRequest http) {
        rateLimiter.check(
                "contact:ip:" + http.getRemoteAddr(),
                5,
                ONE_HOUR,
                "You've sent several messages already. Please try again in an hour or call us.");
        rateLimiter.check(
                "contact:email:" + request.getEmail().trim().toLowerCase(Locale.ROOT),
                3,
                ONE_HOUR,
                "You've sent several messages already. Please try again in an hour or call us.");
        contactService.sendMessage(request);
        return ResponseEntity.ok(Map.of("message", "Thanks! Your message has been sent. We'll reply by email."));
    }
}
