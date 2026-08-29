package com.dropshipshop.api.catalog.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.catalog.domain.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

	Optional<Supplier> findByName(String name);

	@Query("""
		select (count(supplier) > 0)
		from Supplier supplier
		where supplier.email is not null
		  and lower(trim(supplier.email)) = lower(trim(:email))
		""")
	boolean existsByCanonicalEmail(@Param("email") String email);

	@Query("""
		select (count(supplier) > 0)
		from Supplier supplier
		where supplier.id <> :id
		  and supplier.email is not null
		  and lower(trim(supplier.email)) = lower(trim(:email))
		""")
	boolean existsByCanonicalEmailAndIdNot(@Param("email") String email, @Param("id") UUID id);

	Optional<Supplier> findByManagerUserId(UUID managerUserId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select supplier from Supplier supplier where supplier.id = :id")
	Optional<Supplier> findByIdForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select supplier from Supplier supplier where supplier.managerUserId = :managerUserId")
	Optional<Supplier> findByManagerUserIdForUpdate(@Param("managerUserId") UUID managerUserId);

	List<Supplier> findTop100ByContactRetentionExpiresAtLessThanEqualAndContactAnonymizedAtIsNullOrderByContactRetentionExpiresAtAsc(
		Instant now
	);
}
