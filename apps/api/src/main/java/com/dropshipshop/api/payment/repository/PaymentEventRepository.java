package com.dropshipshop.api.payment.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.payment.domain.PaymentEvent;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {
}
