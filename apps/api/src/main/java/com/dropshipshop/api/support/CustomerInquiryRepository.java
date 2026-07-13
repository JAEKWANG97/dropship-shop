package com.dropshipshop.api.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.dropshipshop.api.support.domain.CustomerInquiry;
import com.dropshipshop.api.support.domain.CustomerInquiryStatus;

interface CustomerInquiryRepository extends JpaRepository<CustomerInquiry, UUID> {

	List<CustomerInquiry> findAllByOrderByCreatedAtDesc();

	List<CustomerInquiry> findAllByStatusOrderByCreatedAtDesc(CustomerInquiryStatus status);

	long countByEmailAndCreatedAtAfter(String email, Instant createdAfter);

	@Modifying
	@Query("delete from CustomerInquiry inquiry where inquiry.retentionExpiresAt < :now")
	int deleteExpired(Instant now);
}
