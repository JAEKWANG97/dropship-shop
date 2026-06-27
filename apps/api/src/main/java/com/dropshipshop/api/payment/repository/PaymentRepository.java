package com.dropshipshop.api.payment.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	Optional<Payment> findByProviderPaymentKey(String providerPaymentKey);

	Optional<Payment> findFirstByPaymentGroup_IdOrderByCreatedAtDesc(UUID paymentGroupId);
}
