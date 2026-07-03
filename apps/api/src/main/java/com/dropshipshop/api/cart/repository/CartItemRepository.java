package com.dropshipshop.api.cart.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.cart.domain.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

	@EntityGraph(attributePaths = {"product", "productOption"})
	List<CartItem> findAllByCart_User_IdOrderByCreatedAtAsc(UUID userId);

	@EntityGraph(attributePaths = {"product", "productOption"})
	List<CartItem> findAllByCart_IdOrderByCreatedAtAsc(UUID cartId);

	Optional<CartItem> findByCart_IdAndProductOption_Id(UUID cartId, UUID productOptionId);

	Optional<CartItem> findByIdAndCart_User_Id(UUID id, UUID userId);
}
