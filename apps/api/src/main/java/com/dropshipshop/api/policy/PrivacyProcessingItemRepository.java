package com.dropshipshop.api.policy;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.policy.domain.PrivacyProcessingItem;

interface PrivacyProcessingItemRepository extends JpaRepository<PrivacyProcessingItem, UUID> {

	List<PrivacyProcessingItem> findAllByActiveTrueOrderBySortOrderAsc();
}
