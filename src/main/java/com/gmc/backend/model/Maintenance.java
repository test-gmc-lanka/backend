package com.gmc.backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "maintenance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Maintenance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maintenance_id")
    private Long maintenanceId;

    @NotBlank(message = "Description is required")
    @Column(length = 2000, nullable = false)
    private String description;

    @NotBlank(message = "Status is required")
    @Column(nullable = false)
    private String status;

    @NotNull(message = "Cost is required")
    @DecimalMin(value = "0.0", message = "Cost must be at least 0")
    @Column(nullable = false)
    private BigDecimal cost;

    @NotNull(message = "User is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Null for custom design work that is not a catalog product (title is used instead)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // Date the work must be finished by
    @Column(name = "deadline")
    private LocalDate deadline;

    // Name of the custom design when the task is not tied to a product
    @Column
    private String title;

    // Reference images the admin attaches for the technician
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "maintenance_images", joinColumns = @JoinColumn(name = "maintenance_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url", nullable = false)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();
}
