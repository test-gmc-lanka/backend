package com.gmc.backend.repository;

import com.gmc.backend.model.CustomDesign;
import com.gmc.backend.model.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomDesignRepository extends JpaRepository<CustomDesign, Long> {

    List<CustomDesign> findByUser(User user);

    List<CustomDesign> findByUser_UserId(Long userId);

    List<CustomDesign> findByDesignNameContainingIgnoreCase(String name);
}
