package com.gmc.backend.repository;

import com.gmc.backend.model.Order;
import com.gmc.backend.model.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder(Order order);
}
