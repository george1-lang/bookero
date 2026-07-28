package com.bookero.auth;

import com.bookero.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuthServiceTest {
    @Autowired
    private AuthService authService;

    @Test
    void loginWithValidAnalystCredentials() {
        LoginRequest request = new LoginRequest("analyst@bookero.local", "password");
        LoginResponse response = authService.login(request);

        assertNotNull(response.token());
        assertEquals("ANALYST", response.role());
        assertEquals("analyst@bookero.local", response.email());
    }

    @Test
    void loginWithValidTravelerCredentials() {
        LoginRequest request = new LoginRequest("traveler@bookero.local", "password");
        LoginResponse response = authService.login(request);

        assertNotNull(response.token());
        assertEquals("TRAVELER", response.role());
        assertEquals("traveler@bookero.local", response.email());
    }

    @Test
    void loginWithWrongPasswordThrowsGenericError() {
        LoginRequest request = new LoginRequest("analyst@bookero.local", "wrongpassword");
        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request));

        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    void loginWithUnknownEmailThrowsGenericError() {
        LoginRequest request = new LoginRequest("unknown@bookero.local", "password");
        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request));

        assertEquals("Invalid email or password", ex.getMessage());
    }
}
