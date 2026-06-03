package com.gmc.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "custom_designs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomDesign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "design_id")
    private Long designId;

    @NotBlank(message = "Design name is required")
    @Column(name = "design_name", nullable = false)
    private String designName;

    @NotBlank(message = "Description is required")
    @Column(length = 2000, nullable = false)
    private String description;

    @NotNull(message = "Created date is required")
    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @NotNull(message = "User is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

}
