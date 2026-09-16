package com.example.orders.repository;

import com.example.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);

    // Must run inside a transaction: the lock lasts until PROCESSING is committed.
    @Query(value = """
            SELECT * FROM orders
            WHERE status = 'PENDING'
            ORDER BY created_at, id
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Order> lockNextPending();
}
