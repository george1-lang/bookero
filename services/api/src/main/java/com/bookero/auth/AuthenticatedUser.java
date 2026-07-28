package com.bookero.auth;

import java.util.UUID;

public record AuthenticatedUser(
    UUID id,
    String email,
    Role role
) {
}
