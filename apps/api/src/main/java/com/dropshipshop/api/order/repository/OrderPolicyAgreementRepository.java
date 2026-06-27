package com.dropshipshop.api.order.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.order.domain.OrderPolicyAgreement;

public interface OrderPolicyAgreementRepository extends JpaRepository<OrderPolicyAgreement, UUID> {

	Optional<OrderPolicyAgreement> findByPaymentGroup_Id(UUID paymentGroupId);
}
