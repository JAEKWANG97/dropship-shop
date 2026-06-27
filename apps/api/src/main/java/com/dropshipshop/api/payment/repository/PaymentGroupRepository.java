package com.dropshipshop.api.payment.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.payment.domain.PaymentGroup;

public interface PaymentGroupRepository extends JpaRepository<PaymentGroup, UUID> {

	Optional<PaymentGroup> findByCheckoutNumberAndUser_Id(String checkoutNumber, UUID userId);

	boolean existsByCheckoutNumber(String checkoutNumber);
}
