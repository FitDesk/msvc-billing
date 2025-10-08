package com.msvcbilling.services;


import org.springframework.security.core.Authentication;

import java.util.UUID;

public interface AuthorizationService {

    boolean hasRole(Authentication authentication, String role);

    UUID getUserId(Authentication authentication);

    boolean canAccessResource(UUID ownerId, Authentication authentication);

    boolean canAccessWithRoleOrOwner(UUID ownerId, Authentication authentication, String role);
}