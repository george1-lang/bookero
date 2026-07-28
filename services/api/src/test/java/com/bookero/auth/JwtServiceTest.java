package com.bookero.auth;

import com.bookero.common.BookeroProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private final JwtService jwtService = new JwtService(
        new BookeroProperties("super-secret-key-that-is-32-characters-long!!!", 720, "http://localhost:8001",
            List.of("http://localhost:3000"), true, "time_pressure_heuristic")
    );

    @Test
    void roundTripToken() {
        UUID userId = UUID.randomUUID();
        String email = "test@bookero.local";
        Role role = Role.ANALYST;

        String token = jwtService.issueToken(userId, email, role);
        assertNotNull(token);

        var result = jwtService.parseToken(token);
        assertTrue(result.isPresent());

        AuthenticatedUser user = result.get();
        assertEquals(userId, user.id());
        assertEquals(email, user.email());
        assertEquals(role, user.role());
    }

    @Test
    void tamperWithTokenRejectsFail() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueToken(userId, "test@bookero.local", Role.TRAVELER);

        String tamperedToken = token.substring(0, token.length() - 1) + "X";
        var result = jwtService.parseToken(tamperedToken);
        assertTrue(result.isEmpty());
    }

    @Test
    void invalidTokenReturnsEmpty() {
        var result = jwtService.parseToken("invalid.token.value");
        assertTrue(result.isEmpty());
    }
}
