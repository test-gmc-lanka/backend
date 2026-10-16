package com.gmc.backend.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.OrderStatus;
import com.gmc.backend.model.PaymentMethod;
import com.gmc.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUser(User user);

    List<Order> findByStatus(OrderStatus status);

    Optional<Order> findByStripePaymentIntentId(String stripePaymentIntentId);

    // Row lock so two status changes (e.g. a double-clicked cancel) can't both restock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    // Unpaid-order expiry: card orders still PENDING that were placed before the cutoff
    List<Order> findByPaymentMethodAndStatusAndOrderDateBefore(
            PaymentMethod paymentMethod, OrderStatus status, LocalDateTime cutoff);
}
