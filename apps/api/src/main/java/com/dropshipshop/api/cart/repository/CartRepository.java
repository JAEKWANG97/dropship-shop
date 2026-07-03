package com.dropshipshop.api.cart.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.cart.domain.Cart;

import jakarta.persistence.LockModeType;

public interface CartRepository extends JpaRepository<Cart, UUID> {

	Optional<Cart> findByUser_Id(UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Cart c where c.user.id = :userId")
	Optional<Cart> findByUserIdForUpdate(@Param("userId") UUID userId);
}
