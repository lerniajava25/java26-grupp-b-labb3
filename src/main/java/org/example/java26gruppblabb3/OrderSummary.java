package org.example.java26gruppblabb3;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderSummary(
        UUID id,
        String address,
        String notes,
        String status,
        LocalDateTime createdAt,
        int photoCount
) {
}
