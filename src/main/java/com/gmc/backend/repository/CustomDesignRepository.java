package com.gmc.backend.repository;

import com.gmc.backend.model.CustomDesign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomDesignRepository extends JpaRepository<CustomDesign, Long> {
}
