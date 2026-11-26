package org.example.java26gruppblabb3;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private static final TypeReference<List<GeoPoint>> BOUNDARY_TYPE = new TypeReference<>() {
    };

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public OrderController(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<OrderSummary> getOpenOrders() {
        return orderService.getOpenOrders();
    }

    @GetMapping("/{orderId}")
    public OrderDetails getOrder(@PathVariable UUID orderId) {
        return orderService.getOrder(orderId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OrderDetails> createOrder(
            @RequestParam String customerName,
            @RequestParam String customerContact,
            @RequestParam String address,
            @RequestParam(defaultValue = "") String notes,
            @RequestParam String boundary,
            @RequestParam(required = false) MultipartFile[] photos
    ) {
        List<GeoPoint> points;
        try {
            points = objectMapper.readValue(boundary, BOUNDARY_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("The selected area must be valid JSON.");
        }

        List<PhotoUpload> photoUploads = new ArrayList<>();
        if (photos != null) {
            for (MultipartFile photo : photos) {
                try {
                    photoUploads.add(new PhotoUpload(photo.getOriginalFilename(), photo.getContentType(), photo.getBytes()));
                } catch (IOException exception) {
                    throw new IllegalArgumentException("A photo could not be read.");
                }
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(
                customerName, customerContact, address, notes, points, photoUploads));
    }

    @PostMapping("/{orderId}/claim")
    public OrderDetails claimOrder(@PathVariable UUID orderId, @RequestBody ClaimRequest request) {
        return orderService.claimOrder(orderId, request.clearerName(), request.clearerContact());
    }

    @GetMapping("/{orderId}/photos/{photoId}")
    public ResponseEntity<byte[]> getPhoto(@PathVariable UUID orderId, @PathVariable UUID photoId) {
        String contentType = orderService.getPhotoContentType(orderId, photoId);
        byte[] image = orderService.getPhoto(orderId, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(image);
    }
}
