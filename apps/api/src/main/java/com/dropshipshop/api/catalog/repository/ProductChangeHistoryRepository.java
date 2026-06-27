package com.dropshipshop.api.catalog.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.catalog.domain.ProductChangeHistory;

public interface ProductChangeHistoryRepository extends JpaRepository<ProductChangeHistory, UUID> {
}
