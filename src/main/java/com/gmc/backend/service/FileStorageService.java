package com.gmc.backend.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    // Saves the images under uploads/{folder}/ and returns their public /uploads/... URLs
    List<String> storeImages(List<MultipartFile> files, String folder);
}
