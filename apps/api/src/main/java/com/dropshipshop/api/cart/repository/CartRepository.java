package com.dropshipshop.api.cart.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.cart.domain.Cart;

public interface CartRepository extends JpaRepository<Cart, UUID> {

	Optional<Cart> findByUser_Id(UUID userId);
}
