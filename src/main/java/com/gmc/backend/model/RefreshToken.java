package com.gmc.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Stores hashed refresh tokens. Using ManyToOne so old (used=true) tokens can
 * be retained for reuse-detection before garbage collection.
 *
 * NOTE: if the table already exists with a UNIQUE constraint on user_id from a
 * prior OneToOne mapping, drop and recreate it:
 *   DROP TABLE refresh_token;
 * Hibernate (ddl-auto=update) will recreate it without the constraint.
 */
@Entity
@Table(name = "refresh_token")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hash of the raw token sent to the client — never store the raw value. */
    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    /** True once this token has been rotated or invalidated. */
    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;
}
