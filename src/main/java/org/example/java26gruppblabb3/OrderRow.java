package org.example.java26gruppblabb3;

import java.time.LocalDateTime;
import java.util.UUID;

record OrderRow(
        UUID id,
        String customerName,
        String customerContact,
        String address,
        String notes,
        String boundaryJson,
        String status,
        String clearerName,
        String clearerContact,
        LocalDateTime createdAt
) {
}
