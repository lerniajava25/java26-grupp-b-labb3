package org.example.java26gruppblabb3;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OrderService {
    private static final int MAX_PHOTOS = 5;
    private static final int MAX_PHOTO_BYTES = 10 * 1024 * 1024;
    private static final long MAX_TOTAL_PHOTO_BYTES = 50L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 12_000;
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;
    private static final TypeReference<List<GeoPoint>> BOUNDARY_TYPE = new TypeReference<>() {
    };

    private final OrderRepository repository;
    private final PhotoStorage photoStorage;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository repository, PhotoStorage photoStorage, ObjectMapper objectMapper) {
        this.repository = repository;
        this.photoStorage = photoStorage;
        this.objectMapper = objectMapper;
    }

    public List<OrderSummary> getOpenOrders() {
        return repository.findOpenOrders();
    }

    public OrderDetails getOrder(UUID id) {
        return toDetails(findOrder(id));
    }

    @Transactional
    public OrderDetails createOrder(
            String customerName,
            String customerContact,
            String address,
            String notes,
            List<GeoPoint> boundary,
            List<PhotoUpload> photos
    ) {
        String normalizedCustomerName = requiredText(customerName, "Customer name", 100);
        String normalizedCustomerContact = requiredText(customerContact, "Contact information", 200);
        String normalizedAddress = requiredText(address, "Address", 200);
        String normalizedNotes = optionalText(notes, "Notes", 2_000);
        PolygonValidator.validate(boundary);
        List<ValidatedPhoto> validPhotos = validatePhotos(photos);

        String boundaryJson;
        try {
            boundaryJson = objectMapper.writeValueAsString(boundary);
        } catch (JacksonException _) {
            throw new IllegalArgumentException("The selected area could not be saved.");
        }

        UUID orderId = UUID.randomUUID();
        List<String> uploadedKeys = new ArrayList<>();
        try {
            for (ValidatedPhoto photo : validPhotos) {
                String key = "orders/" + orderId + "/" + UUID.randomUUID();
                try {
                    photoStorage.put(key, photo.contentType(), photo.bytes());
                } catch (RuntimeException exception) {
                    throw new PhotoStorageException("Photo storage is unavailable. Check Garage settings and bucket.", exception);
                }
                uploadedKeys.add(key);
                photo.objectKey(key);
            }

            repository.insertOrder(orderId, normalizedCustomerName, normalizedCustomerContact, normalizedAddress,
                    normalizedNotes, boundaryJson, LocalDateTime.now());
            for (ValidatedPhoto photo : validPhotos) {
                repository.insertPhoto(photo.id(), orderId, photo.objectKey(), photo.fileName(), photo.contentType());
            }
            return getOrder(orderId);
        } catch (RuntimeException exception) {
            for (String key : uploadedKeys) {
                try {
                    photoStorage.delete(key);
                } catch (RuntimeException _) {
                    // Keep the original creation failure; the object key is otherwise not exposed.
                }
            }
            throw exception;
        }
    }

    @Transactional
    public OrderDetails claimOrder(UUID orderId, String clearerName, String clearerContact) {
        String normalizedName = requiredText(clearerName, "Clearer name", 100);
        String normalizedContact = requiredText(clearerContact, "Contact information", 200);
        if (repository.claim(orderId, normalizedName, normalizedContact) == 0) {
            if (repository.findOrder(orderId).isEmpty()) {
                throw new OrderNotFoundException("Order not found.");
            }
            throw new OrderConflictException("This order has already been claimed.");
        }
        return getOrder(orderId);
    }

    public byte[] getPhoto(UUID orderId, UUID photoId) {
        OrderPhotoRow photo = repository.findPhoto(orderId, photoId)
                .orElseThrow(() -> new OrderNotFoundException("Photo not found."));
        try {
            return photoStorage.get(photo.objectKey());
        } catch (RuntimeException exception) {
            throw new PhotoStorageException("Photo storage is unavailable. Check Garage settings and bucket.", exception);
        }
    }

    public String getPhotoContentType(UUID orderId, UUID photoId) {
        return repository.findPhoto(orderId, photoId)
                .orElseThrow(() -> new OrderNotFoundException("Photo not found."))
                .contentType();
    }

    private OrderRow findOrder(UUID id) {
        return repository.findOrder(id).orElseThrow(() -> new OrderNotFoundException("Order not found."));
    }

    private OrderDetails toDetails(OrderRow row) {
        List<GeoPoint> boundary;
        try {
            boundary = objectMapper.readValue(row.boundaryJson(), BOUNDARY_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Saved area geometry is unreadable.", exception);
        }
        List<PhotoDetails> photos = repository.findPhotos(row.id()).stream()
                .map(photo -> new PhotoDetails(photo.id(), photo.fileName(), photo.contentType(),
                        "/api/orders/" + row.id() + "/photos/" + photo.id()))
                .toList();
        return new OrderDetails(row.id(), row.customerName(), row.customerContact(), row.address(), row.notes(),
                boundary, row.status(), row.clearerName(), row.clearerContact(), row.createdAt(), photos);
    }

    private List<ValidatedPhoto> validatePhotos(List<PhotoUpload> photos) {
        if (photos == null || photos.isEmpty()) {
            return List.of();
        }
        if (photos.size() > MAX_PHOTOS) {
            throw new IllegalArgumentException("Upload no more than five photos.");
        }

        long totalBytes = 0;
        List<ValidatedPhoto> validated = new ArrayList<>();
        for (PhotoUpload photo : photos) {
            if (photo == null || photo.bytes() == null || photo.bytes().length == 0) {
                throw new IllegalArgumentException("Uploaded photos cannot be empty.");
            }
            if (photo.bytes().length > MAX_PHOTO_BYTES) {
                throw new IllegalArgumentException("Each photo must be 10 MB or smaller.");
            }
            totalBytes += photo.bytes().length;
            if (totalBytes > MAX_TOTAL_PHOTO_BYTES) {
                throw new IllegalArgumentException("The combined photo upload must be 50 MB or smaller.");
            }
            String detectedType = detectImageType(photo.bytes());
            String declaredType = photo.contentType() == null ? "" : photo.contentType().toLowerCase(Locale.ROOT);
            if (!detectedType.equals(declaredType)) {
                throw new IllegalArgumentException("Photos must be valid JPEG, PNG, or GIF images.");
            }
            validated.add(new ValidatedPhoto(UUID.randomUUID(), safeFileName(photo.fileName()), detectedType, photo.bytes()));
        }
        return validated;
    }

    private String detectImageType(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IllegalArgumentException("Photos must be valid JPEG, PNG, or GIF images.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Photos must be valid JPEG, PNG, or GIF images.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String contentType = switch (format) {
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "gif" -> "image/gif";
                    default -> throw new IllegalArgumentException("Photos must be valid JPEG, PNG, or GIF images.");
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IllegalArgumentException("Photo dimensions are too large.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new IllegalArgumentException("Photos must be valid JPEG, PNG, or GIF images.");
                }
                return contentType;
            } finally {
                reader.dispose();
            }
        } catch (IOException _) {
            throw new IllegalArgumentException("Photos must be valid JPEG, PNG, or GIF images.");
        }
    }

    private String safeFileName(String fileName) {
        String normalized = fileName == null ? "photo" : fileName.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        normalized = normalized.substring(separator + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
        if (normalized.isEmpty()) {
            return "photo";
        }
        return normalized.length() > 200 ? normalized.substring(normalized.length() - 200) : normalized;
    }

    private String requiredText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be " + maxLength + " characters or fewer.");
        }
        return normalized;
    }

    private String optionalText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be " + maxLength + " characters or fewer.");
        }
        return normalized;
    }

    private static final class ValidatedPhoto {
        private final UUID id;
        private final String fileName;
        private final String contentType;
        private final byte[] bytes;
        private String objectKey;

        private ValidatedPhoto(UUID id, String fileName, String contentType, byte[] bytes) {
            this.id = id;
            this.fileName = fileName;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        private UUID id() {
            return id;
        }

        private String fileName() {
            return fileName;
        }

        private String contentType() {
            return contentType;
        }

        private byte[] bytes() {
            return bytes;
        }

        private String objectKey() {
            return objectKey;
        }

        private void objectKey(String objectKey) {
            this.objectKey = objectKey;
        }
    }
}
