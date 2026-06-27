package com.dropshipshop.api.auth.security;

import java.util.UUID;

import com.dropshipshop.api.user.domain.UserRole;

public record AuthenticatedUser(UUID userId, UserRole role) {
}
