package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);

    Optional<PaymentTransaction> findByRazorpayPaymentId(String razorpayPaymentId);

    /**
     * Loads the transaction under a pessimistic write lock so concurrent fulfilment attempts (verify
     * endpoint + webhook) serialise on the row — the second sees status SUCCESS and no-ops.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.razorpayOrderId = :orderId")
    Optional<PaymentTransaction> findByRazorpayOrderIdForUpdate(@Param("orderId") String orderId);
}
