package com.gmc.backend.service;

import com.gmc.backend.dto.request.FeedbackRequest;
import com.gmc.backend.dto.request.FeedbackUpdateRequest;
import com.gmc.backend.dto.response.FeedbackResponse;
import com.gmc.backend.dto.response.ProductReviewsResponse;
import java.util.List;

public interface FeedbackService {

    FeedbackResponse submitFeedback(Long userId, FeedbackRequest request);

    /** Customers can edit only their own review. */
    FeedbackResponse updateFeedback(Long userId, Long feedbackId, FeedbackUpdateRequest request);

    /** Admin moderation: hidden reviews disappear from the product page and its average. */
    FeedbackResponse setHidden(Long feedbackId, boolean hidden);

    List<FeedbackResponse> getFeedbackByUser(Long userId);

    List<FeedbackResponse> getAllFeedback();

    List<FeedbackResponse> getFeedbackByProduct(Long productId);

    /** Public: average rating and reviews shown on the product page. */
    ProductReviewsResponse getProductReviews(Long productId);
}
