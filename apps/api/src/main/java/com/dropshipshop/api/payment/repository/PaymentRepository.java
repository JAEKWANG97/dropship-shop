package com.dropshipshop.api.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	Optional<Payment> findByProviderPaymentKey(String providerPaymentKey);

	Optional<Payment> findFirstByPaymentGroup_IdOrderByCreatedAtDesc(UUID paymentGroupId);

	List<Payment> findAllByStatusInOrderByCreatedAtAsc(Collection<PaymentStatus> statuses);
}
