package com.gmc.backend.service.impl;

import com.gmc.backend.dto.response.CustomDesignResponse;
import com.gmc.backend.exception.InvalidFileException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.CustomDesign;
import com.gmc.backend.model.CustomDesignStatus;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.CustomDesignRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.CustomDesignService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomDesignServiceImpl implements CustomDesignService {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("svg", "dxf", "pdf", "png", "jpg", "jpeg", "cad", "dwg");

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    private final CustomDesignRepository customDesignRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CustomDesignResponse submitDesign(Long userId, String designName, String description,
                                              MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        String filePath = null;
        if (file != null && !file.isEmpty()) {
            filePath = storeFile(file, userId);
        }
        CustomDesign design = CustomDesign.builder()
                .designName(designName)
                .description(description)
                .filePath(filePath)
                .status(CustomDesignStatus.PENDING_REVIEW)
                .createdDate(LocalDateTime.now())
                .user(user)
                .build();
        return toResponse(customDesignRepository.save(design));
    }

    @Override
    public List<CustomDesignResponse> getDesignsByUser(Long userId) {
        return customDesignRepository.findByUser_UserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CustomDesignResponse> getAllDesigns() {
        return customDesignRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CustomDesignResponse getDesignById(Long designId) {
        return toResponse(customDesignRepository.findById(designId)
                .orElseThrow(() -> new ResourceNotFoundException("Design not found: " + designId)));
    }

    @Override
    @Transactional
    public CustomDesignResponse updateStatus(Long designId, CustomDesignStatus status) {
        CustomDesign design = customDesignRepository.findById(designId)
                .orElseThrow(() -> new ResourceNotFoundException("Design not found: " + designId));
        design.setStatus(status);
        return toResponse(customDesignRepository.save(design));
    }

    private String storeFile(MultipartFile file, Long userId) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidFileException("File name is missing");
        }
        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new InvalidFileException("File type ." + extension + " is not allowed");
        }
        try {
            Path uploadPath = Paths.get(uploadDir, "designs", String.valueOf(userId));
            Files.createDirectories(uploadPath);
            String uniqueName = UUID.randomUUID() + "." + extension;
            Path destination = uploadPath.resolve(uniqueName);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/designs/" + userId + "/" + uniqueName;
        } catch (IOException e) {
            throw new InvalidFileException("Failed to store file: " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot + 1) : "";
    }

    private CustomDesignResponse toResponse(CustomDesign d) {
        return CustomDesignResponse.builder()
                .designId(d.getDesignId())
                .designName(d.getDesignName())
                .description(d.getDescription())
                .filePath(d.getFilePath())
                .status(d.getStatus())
                .createdDate(d.getCreatedDate())
                .userId(d.getUser().getUserId())
                .userName(d.getUser().getName())
                .build();
    }
}
