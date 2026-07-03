package com.gmc.backend.controller;

import com.gmc.backend.dto.response.CustomDesignResponse;
import com.gmc.backend.security.CustomUserDetails;
import com.gmc.backend.service.CustomDesignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/custom-designs")
@RequiredArgsConstructor
public class CustomDesignController {

    private final CustomDesignService customDesignService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CustomDesignResponse> submit(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam String designName,
            @RequestParam String description,
            @RequestParam(required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(customDesignService.submitDesign(user.getId(), designName, description, file));
    }

    @GetMapping
    public ResponseEntity<List<CustomDesignResponse>> getMyDesigns(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(customDesignService.getDesignsByUser(user.getId()));
    }
}
