package com.dropshipshop.api.catalog.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.catalog.domain.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

	boolean existsByName(String name);
}
