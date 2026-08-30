package com.dropshipshop.api.supplierfulfillment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/admin/supplier-pii-access-logs")
@PreAuthorize("hasRole('ADMIN')")
class AdminSupplierPiiAccessLogController {

	private final SupplierPiiAccessLogRepository repository;

	AdminSupplierPiiAccessLogController(SupplierPiiAccessLogRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	SupplierOrderDtos.AccessLogListResponse list() {
		return new SupplierOrderDtos.AccessLogListResponse(repository.findAllByOrderByAccessedAtDesc().stream()
			.map(log -> new SupplierOrderDtos.AccessLogResponse(
				log.getId(), log.getActorUser().getId(), log.getOrder().getId(), log.getOrder().getOrderNumber(),
				log.getAccessReason(), log.getAccessedAt()
			)).toList());
	}
}
