package com.dropshipshop.api.support;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.support.domain.CustomerInquiry;

interface CustomerInquiryRepository extends JpaRepository<CustomerInquiry, UUID> {

	List<CustomerInquiry> findAllByOrderByCreatedAtDesc();
}
