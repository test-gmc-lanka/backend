package com.gmc.backend.repository;

import com.gmc.backend.model.Product;
import com.gmc.backend.model.ProductCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategory(ProductCategory category);

    List<Product> findByNameContainingIgnoreCase(String name);

    boolean existsByCategory(ProductCategory category);

    // Row lock for stock changes, so concurrent checkouts can't oversell (needs a transaction)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
