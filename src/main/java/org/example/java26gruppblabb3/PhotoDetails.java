package org.example.java26gruppblabb3;

import java.util.UUID;

public record PhotoDetails(UUID id, String fileName, String contentType, String url) {
}
