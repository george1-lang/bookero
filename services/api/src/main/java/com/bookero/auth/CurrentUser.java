package com.bookero.auth;

import com.bookero.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class CurrentUser {
    private CurrentUser() {
    }

    public static AuthenticatedUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw ApiException.unauthorized("Authentication required");
        }
        if (!(auth.getPrincipal() instanceof AuthenticatedUser)) {
            throw ApiException.unauthorized("Authentication required");
        }
        return (AuthenticatedUser) auth.getPrincipal();
    }
}
