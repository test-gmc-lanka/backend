package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Public reviews for a product page; reviewer names are shortened (e.g. "Kamal P.") for privacy. */
@Data
@Builder
public class ProductReviewsResponse {

    private BigDecimal averageRating;
    private int reviewCount;
    private List<Review> reviews;

    @Data
    @Builder
    public static class Review {
        private Long reviewId;
        private String reviewerName;
        private Integer rating;
        private String comment;
        private LocalDateTime createdAt;
        private boolean edited;
        private boolean verifiedPurchase;
    }
}
