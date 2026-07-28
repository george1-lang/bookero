package com.bookero.auth;

public record LoginResponse(
    String token,
    String role,
    String email
) {
}
