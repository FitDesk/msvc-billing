package com.msvcbilling.services.impl;

import com.msvcbilling.services.AuthorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class AuthorizationServiceImpl implements AuthorizationService {
    @Override
    public boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    @Override
    public UUID getUserId(Authentication authentication) {
        String userIdStr = ((JwtAuthenticationToken) authentication).getToken().getClaim("user_id");
        return UUID.fromString(userIdStr);
    }

    @Override
    // Verifica si el usuario es admin o dueño del recurso
    public boolean canAccessResource(UUID ownerId, Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN") || ownerId.equals(getUserId(authentication));
    }

    @Override
    // Verifica si el usuario tiene un rol específico o es dueño del recurso
    public boolean canAccessWithRoleOrOwner(UUID ownerId, Authentication authentication, String role) {
        return hasRole(authentication, role) || ownerId.equals(getUserId(authentication));
    }
}