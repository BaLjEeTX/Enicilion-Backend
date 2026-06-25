package com.enicilion.backend.payments.repository;

import com.enicilion.backend.payments.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.enicilion.backend.payments.entity.PaymentStatus;
import com.enicilion.backend.payments.entity.PaymentProvider;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findByProviderTxId(String providerTxId);
    java.util.List<Payment> findAllByOrderByCreatedAtDesc();

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = "SELECT p FROM Payment p JOIN p.user u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Payment p JOIN p.user u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Payment> searchTransactions(@Param("search") String search, Pageable pageable);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status AND p.provider = :provider")
    BigDecimal sumAmountByStatusAndProvider(@Param("status") PaymentStatus status, @Param("provider") PaymentProvider provider);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status AND p.provider = :provider")
    long countByStatusAndProvider(@Param("status") PaymentStatus status, @Param("provider") PaymentProvider provider);

    // Event-filtered queries
    @Query(value = "SELECT p FROM Payment p JOIN p.user u WHERE p.id IN (SELECT DISTINCT st.payment.id FROM SpectatorTicket st WHERE st.event.id = :eventId) AND (LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))) ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Payment p JOIN p.user u WHERE p.id IN (SELECT DISTINCT st.payment.id FROM SpectatorTicket st WHERE st.event.id = :eventId) AND (LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Payment> searchTransactionsByEvent(@Param("eventId") UUID eventId, @Param("search") String search, Pageable pageable);

    @Query(value = "SELECT p FROM Payment p WHERE p.id IN (SELECT DISTINCT st.payment.id FROM SpectatorTicket st WHERE st.event.id = :eventId) ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Payment p WHERE p.id IN (SELECT DISTINCT st.payment.id FROM SpectatorTicket st WHERE st.event.id = :eventId)")
    Page<Payment> findAllByEventOrderByCreatedAtDesc(@Param("eventId") UUID eventId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.id IN (SELECT DISTINCT st.payment.id FROM SpectatorTicket st WHERE st.event.id = :eventId) AND p.status = :status AND p.provider = :provider")
    BigDecimal sumAmountByEventAndStatusAndProvider(@Param("eventId") UUID eventId, @Param("status") PaymentStatus status, @Param("provider") PaymentProvider provider);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.id IN (SELECT DISTINCT st.payment.id FROM SpectatorTicket st WHERE st.event.id = :eventId) AND p.status = :status AND p.provider = :provider")
    long countByEventAndStatusAndProvider(@Param("eventId") UUID eventId, @Param("status") PaymentStatus status, @Param("provider") PaymentProvider provider);
}
