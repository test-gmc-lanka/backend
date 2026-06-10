package com.gmc.backend.repository;

import com.gmc.backend.model.Order;
import com.gmc.backend.model.OrderStatus;
import com.gmc.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUser(User user);

    List<Order> findByStatus(OrderStatus status);

}

