package org.example.java26gruppblabb3;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderDetails(
        UUID id,
        String customerName,
        String customerContact,
        String address,
        String notes,
        List<GeoPoint> boundary,
        String status,
        String clearerName,
        String clearerContact,
        LocalDateTime createdAt,
        List<PhotoDetails> photos
) {
}
