package com.gmc.backend.service;

import com.gmc.backend.dto.request.FeedbackRequest;
import com.gmc.backend.dto.response.FeedbackResponse;

import java.util.List;

public interface FeedbackService {

    FeedbackResponse submitFeedback(Long userId, FeedbackRequest request);

    List<FeedbackResponse> getFeedbackByUser(Long userId);

    List<FeedbackResponse> getAllFeedback();

    List<FeedbackResponse> getFeedbackByProduct(Long productId);
}
