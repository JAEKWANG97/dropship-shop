package com.dropshipshop.api.user;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.refund.domain.RefundStatus;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;

@Service
class AccountDeletionService {

	private static final EnumSet<OrderStatus> TERMINAL_ORDER_STATUSES = EnumSet.of(
		OrderStatus.DELIVERED,
		OrderStatus.CANCELLED,
		OrderStatus.REFUNDED,
		OrderStatus.EXPIRED
	);

	private static final EnumSet<RefundStatus> ACTIVE_REFUND_STATUSES = EnumSet.complementOf(EnumSet.of(
		RefundStatus.COMPLETED,
		RefundStatus.REJECTED
	));

	private static final EnumSet<ClaimStatus> ACTIVE_CLAIM_STATUSES = EnumSet.complementOf(EnumSet.of(
		ClaimStatus.COMPLETED,
		ClaimStatus.REJECTED,
		ClaimStatus.WITHDRAWN
	));

	private final UserAccountRepository userAccountRepository;
	private final CustomerOrderRepository customerOrderRepository;
	private final RefundRepository refundRepository;
	private final ClaimRepository claimRepository;
	private final SupplierRepository supplierRepository;
	private final Clock clock = Clock.systemUTC();

	AccountDeletionService(
		UserAccountRepository userAccountRepository,
		CustomerOrderRepository customerOrderRepository,
		RefundRepository refundRepository,
		ClaimRepository claimRepository,
		SupplierRepository supplierRepository
	) {
		this.userAccountRepository = userAccountRepository;
		this.customerOrderRepository = customerOrderRepository;
		this.refundRepository = refundRepository;
		this.claimRepository = claimRepository;
		this.supplierRepository = supplierRepository;
	}

	@Transactional
	public void deleteCustomerAccount(UUID userId) {
		if (supplierRepository.findByManagerUserIdForUpdate(userId).isPresent()) {
			throw new ResponseStatusException(
				HttpStatus.CONFLICT,
				"Supplier managers must be disconnected by an administrator before account deletion"
			);
		}
		UserAccount user = userAccountRepository.findByIdForUpdate(userId)
			.filter(account -> account.getStatus() == UserStatus.ACTIVE)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active user account was not found"));
		assertNoActiveCommerceWork(userId);
		Instant now = Instant.now(clock);
		user.deleteAndAnonymize(now);
	}

	private void assertNoActiveCommerceWork(UUID userId) {
		List<String> blockers = new ArrayList<>();
		customerOrderRepository.findTop5ByUser_IdAndStatusNotInOrderByCreatedAtDesc(userId, TERMINAL_ORDER_STATUSES)
			.forEach(order -> blockers.add("주문 " + order.getOrderNumber() + "(" + order.getStatus() + ")"));
		refundRepository.findActiveByUserId(userId, new ArrayList<>(ACTIVE_REFUND_STATUSES))
			.stream()
			.limit(5)
			.forEach(refund -> blockers.add("환불 "
				+ (refund.getOrder() == null
					? "결제그룹 " + refund.getPaymentGroup().getCheckoutNumber()
					: refund.getOrder().getOrderNumber())
				+ "(" + refund.getStatus() + ")"));
		claimRepository.findTop5ByUser_IdAndStatusInOrderByCreatedAtDesc(userId, ACTIVE_CLAIM_STATUSES)
			.forEach(claim -> blockers.add("클레임 " + claim.getOrder().getOrderNumber() + "(" + claim.getStatus() + ")"));
		if (!blockers.isEmpty()) {
			throw new IllegalStateException("진행 중인 주문/환불/클레임이 있어 탈퇴할 수 없습니다: " + String.join(", ", blockers));
		}
	}
}
