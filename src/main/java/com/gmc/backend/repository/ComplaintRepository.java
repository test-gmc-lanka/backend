package com.gmc.backend.repository;

import com.gmc.backend.model.Complaint;
import com.gmc.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findByUser(User user);

    List<Complaint> findByUser_UserId(Long userId);

    List<Complaint> findByStatus(String status);
}
