package com.bookero.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuthControllerIT {
    @Autowired
    private AuthController authController;

    @Autowired
    private JwtService jwtService;

    @Test
    void loginReturnsToken() {
        LoginRequest request = new LoginRequest("analyst@bookero.local", "password");
        var response = authController.login(request);

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().token());
        assertEquals("ANALYST", response.getBody().role());
        assertEquals("analyst@bookero.local", response.getBody().email());
    }

    @Test
    void jwtTokenCanBeValidated() {
        LoginRequest request = new LoginRequest("traveler@bookero.local", "password");
        var loginResponse = authController.login(request);
        String token = loginResponse.getBody().token();

        var parsed = jwtService.parseToken(token);
        assertTrue(parsed.isPresent());
        assertEquals("traveler@bookero.local", parsed.get().email());
        assertEquals("TRAVELER", parsed.get().role().name());
    }
}
