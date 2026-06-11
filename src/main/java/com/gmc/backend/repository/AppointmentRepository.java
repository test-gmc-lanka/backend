package com.gmc.backend.repository;

import com.gmc.backend.model.Appointment;
import com.gmc.backend.model.AppointmentStatus;
import com.gmc.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByUser(User user);

    List<Appointment> findByUserUserId(Long userId);

    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByUserUserIdAndStatus(Long userId, AppointmentStatus status);
}
