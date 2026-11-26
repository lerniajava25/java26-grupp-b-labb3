package org.example.java26gruppblabb3;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OrderRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertOrder(
            UUID id,
            String customerName,
            String customerContact,
            String address,
            String notes,
            String boundaryJson,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO snow_order (id, customer_name, customer_contact, address, notes, boundary_json,
                                                status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?)
                        """,
                id.toString(), customerName, customerContact, address, notes, boundaryJson, createdAt);
    }

    public void insertPhoto(UUID id, UUID orderId, String objectKey, String fileName, String contentType) {
        jdbcTemplate.update("""
                        INSERT INTO snow_order_photo (id, order_id, object_key, file_name, content_type)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                id.toString(), orderId.toString(), objectKey, fileName, contentType);
    }

    public List<OrderSummary> findOpenOrders() {
        return jdbcTemplate.query("""
                        SELECT o.id, o.address, o.notes, o.status, o.created_at,
                               (SELECT COUNT(*) FROM snow_order_photo p WHERE p.order_id = o.id) AS photo_count
                        FROM snow_order o
                        WHERE o.status = 'OPEN'
                        ORDER BY o.created_at DESC
                        """,
                (resultSet, rowNumber) -> new OrderSummary(
                        UUID.fromString(resultSet.getString("id")),
                        resultSet.getString("address"),
                        resultSet.getString("notes"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toLocalDateTime(),
                        resultSet.getInt("photo_count")
                ));
    }

    public Optional<OrderRow> findOrder(UUID id) {
        return jdbcTemplate.query("""
                        SELECT id, customer_name, customer_contact, address, notes, boundary_json, status,
                               clearer_name, clearer_contact, created_at
                        FROM snow_order WHERE id = ?
                        """,
                (resultSet, rowNumber) -> new OrderRow(
                        UUID.fromString(resultSet.getString("id")),
                        resultSet.getString("customer_name"),
                        resultSet.getString("customer_contact"),
                        resultSet.getString("address"),
                        resultSet.getString("notes"),
                        resultSet.getString("boundary_json"),
                        resultSet.getString("status"),
                        resultSet.getString("clearer_name"),
                        resultSet.getString("clearer_contact"),
                        resultSet.getTimestamp("created_at").toLocalDateTime()
                ), id.toString()).stream().findFirst();
    }

    public List<OrderPhotoRow> findPhotos(UUID orderId) {
        return jdbcTemplate.query("""
                        SELECT id, object_key, file_name, content_type
                        FROM snow_order_photo WHERE order_id = ? ORDER BY id
                        """,
                (resultSet, rowNumber) -> new OrderPhotoRow(
                        UUID.fromString(resultSet.getString("id")),
                        resultSet.getString("object_key"),
                        resultSet.getString("file_name"),
                        resultSet.getString("content_type")
                ), orderId.toString());
    }

    public Optional<OrderPhotoRow> findPhoto(UUID orderId, UUID photoId) {
        return jdbcTemplate.query("""
                        SELECT id, object_key, file_name, content_type
                        FROM snow_order_photo WHERE order_id = ? AND id = ?
                        """,
                (resultSet, rowNumber) -> new OrderPhotoRow(
                        UUID.fromString(resultSet.getString("id")),
                        resultSet.getString("object_key"),
                        resultSet.getString("file_name"),
                        resultSet.getString("content_type")
                ), orderId.toString(), photoId.toString()).stream().findFirst();
    }

    public int claim(UUID id, String clearerName, String clearerContact) {
        return jdbcTemplate.update("""
                        UPDATE snow_order
                        SET status = 'CLAIMED', clearer_name = ?, clearer_contact = ?
                        WHERE id = ? AND status = 'OPEN'
                        """,
                clearerName, clearerContact, id.toString());
    }
}
