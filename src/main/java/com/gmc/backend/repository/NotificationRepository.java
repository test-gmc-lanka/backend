package com.gmc.backend.repository;

import com.gmc.backend.model.Notification;
import com.gmc.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    List<Notification> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserAndReadFalse(User user);

    long countByUserAndReadFalse(User user);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user = :user")
    void markAllReadByUser(User user);
}
