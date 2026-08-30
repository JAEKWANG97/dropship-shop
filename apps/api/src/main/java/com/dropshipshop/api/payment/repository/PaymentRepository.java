package com.dropshipshop.api.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	Optional<Payment> findByProviderPaymentKey(String providerPaymentKey);

	Optional<Payment> findFirstByPaymentGroup_IdOrderByCreatedAtDesc(UUID paymentGroupId);

	List<Payment> findAllByStatusInOrderByCreatedAtAsc(Collection<PaymentStatus> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select payment from Payment payment where payment.paymentGroup.id = :paymentGroupId order by payment.createdAt desc")
	List<Payment> findAllByPaymentGroupIdForUpdate(@Param("paymentGroupId") UUID paymentGroupId);
}
