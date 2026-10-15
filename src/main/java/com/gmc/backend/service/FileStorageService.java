package com.gmc.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileStorageService {

    // Saves the images under uploads/{folder}/ and returns their public /uploads/... URLs
    List<String> storeImages(List<MultipartFile> files, String folder);
}
