package com.dropshipshop.api.order.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.order.domain.AdminOrderActionHistory;

public interface AdminOrderActionHistoryRepository extends JpaRepository<AdminOrderActionHistory, UUID> {
}
