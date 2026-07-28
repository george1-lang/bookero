package com.bookero.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "bookero")
public record BookeroProperties(
    String jwtSecret,
    Integer jwtTtlMinutes,
    String analyticsBaseUrl,
    List<String> corsAllowedOrigins,
    Boolean repriceAfterBooking,
    String repriceAfterBookingKey
) {
}
