package com.gmc.backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long appointmentId;

    @NotNull(message = "Appointment date is required")
    @Column(nullable = false)
    private LocalDateTime date;

    @NotBlank(message = "Message is required")
    @Column(nullable = false, length = 1000)
    private String message;

    @NotNull(message = "Appointment status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    // Online meeting URL (Google Meet, Zoom, ...) shared when the admin approves
    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    // Latest note from the admin (approve / reschedule / cancel / complete), shown to the customer
    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    // Product line the customer is asking about (optional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    // Photos/sketches the customer attached to the request
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "appointment_images", joinColumns = @JoinColumn(name = "appointment_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url", nullable = false)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @NotNull(message = "User is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
