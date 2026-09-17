package com.example.orders.repository;

import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;

import com.example.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);

    boolean existsByExternalIdAndIdNot(String externalId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockForRetry(@Param("id") Long id);

    // Must run inside a transaction: the lock lasts until PROCESSING is committed.
    @Query(value = """
            SELECT * FROM orders
            WHERE status = 'PENDING'
            ORDER BY created_at, id
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Order> lockNextPending();
}
