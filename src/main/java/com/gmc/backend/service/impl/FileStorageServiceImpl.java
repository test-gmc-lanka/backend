package com.gmc.backend.service.impl;

import com.gmc.backend.exception.InvalidFileException;
import com.gmc.backend.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp", "gif");

    @Value("${app.upload-dir:uploads/}")
    private String uploadDir;

    @Override
    public List<String> storeImages(List<MultipartFile> files, String folder) {
        if (files == null || files.isEmpty()) {
            throw new InvalidFileException("No images were provided");
        }
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                urls.add(storeImage(file, folder));
            }
        }
        return urls;
    }

    private String storeImage(MultipartFile file, String folder) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidFileException("File name is missing");
        }
        int dot = originalFilename.lastIndexOf('.');
        String extension = dot >= 0 ? originalFilename.substring(dot + 1).toLowerCase() : "";
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new InvalidFileException("Image type ." + extension + " is not allowed");
        }
        try {
            Path uploadPath = Paths.get(uploadDir, folder);
            Files.createDirectories(uploadPath);
            String uniqueName = UUID.randomUUID() + "." + extension;
            Files.copy(file.getInputStream(), uploadPath.resolve(uniqueName),
                    StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + folder + "/" + uniqueName;
        } catch (IOException e) {
            throw new InvalidFileException("Failed to store image: " + e.getMessage());
        }
    }
}
