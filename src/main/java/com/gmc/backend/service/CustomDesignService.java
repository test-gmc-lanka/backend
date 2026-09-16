package com.gmc.backend.service;

import com.gmc.backend.dto.response.CustomDesignResponse;
import com.gmc.backend.model.CustomDesignStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CustomDesignService {

    CustomDesignResponse submitDesign(Long userId, String designName, String description,
                                      MultipartFile file);

    List<CustomDesignResponse> getDesignsByUser(Long userId);

    List<CustomDesignResponse> getAllDesigns();

    CustomDesignResponse getDesignById(Long designId);

    CustomDesignResponse updateStatus(Long designId, CustomDesignStatus status);
}
