package com.gmc.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** A customer changing the rating/text of their own review. */
@Data
public class FeedbackUpdateRequest {

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @NotBlank(message = "Please write a short review")
    @Size(max = 1000, message = "Review must be at most 1000 characters")
    private String comment;
}
