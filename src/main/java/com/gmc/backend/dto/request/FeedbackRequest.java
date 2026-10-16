package com.gmc.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** A product review from a customer who received the product in a delivered order. */
@Data
public class FeedbackRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotNull(message = "Choose the product you are reviewing")
    private Long productId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @NotBlank(message = "Please write a short review")
    @Size(max = 1000, message = "Review must be at most 1000 characters")
    private String comment;
}
