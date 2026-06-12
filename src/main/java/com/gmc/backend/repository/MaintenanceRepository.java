package com.gmc.backend.repository;

import com.gmc.backend.model.Maintenance;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

    List<Maintenance> findByUser(User user);

    List<Maintenance> findByUser_UserId(Long userId);

    List<Maintenance> findByProduct(Product product);

    List<Maintenance> findByStatus(String status);

}

