package org.example.java26gruppblabb3;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:snowclearing;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(SnowOrderIntegrationTests.TestStorageConfiguration.class)
class SnowOrderIntegrationTests {
    private static final String VALID_BOUNDARY = "[{\"lat\":59.32,\"lng\":18.06},{\"lat\":59.32,\"lng\":18.07},"
            + "{\"lat\":59.33,\"lng\":18.07},{\"lat\":59.33,\"lng\":18.06},"
            + "{\"lat\":59.32,\"lng\":18.06}]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakePhotoStorage photoStorage;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM snow_order_photo");
        jdbcTemplate.update("DELETE FROM snow_order");
        photoStorage.clear();
    }

    @Test
    void createsOrderAndRetrievesPhotoThroughStorage() throws Exception {
        byte[] imageBytes = createPng();
        MvcResult result = mockMvc.perform(validOrder(image("area.png", "image/png", imageBytes)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.boundary.length()").value(5))
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andReturn();

        JsonNode order = objectMapper.readTree(result.getResponse().getContentAsString());
        String orderId = order.get("id").asText();
        String photoId = order.get("photos").get(0).get("id").asText();

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].address").value("42 Snow Street"))
                .andExpect(jsonPath("$[0].photoCount").value(1));
        mockMvc.perform(get("/api/orders/{orderId}/photos/{photoId}", orderId, photoId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(imageBytes));
        assertEquals(1, photoStorage.size());
    }

    @Test
    void rejectsOpenPolygonsAndNonImageUploads() throws Exception {
        mockMvc.perform(validOrder(null, "[{\"lat\":59.32,\"lng\":18.06},{\"lat\":59.32,\"lng\":18.07},"
                        + "{\"lat\":59.33,\"lng\":18.07},{\"lat\":59.33,\"lng\":18.06}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(validOrder(image("notes.txt", "text/plain", "not an image".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Photos must be valid JPEG, PNG, or GIF images."));
        mockMvc.perform(get("/api/orders")).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void onlyOneConcurrentClearerCanClaimAnOrder() throws Exception {
        String orderId = createOrderAndGetId();
        byte[] firstClaim = objectMapper.writeValueAsBytes(Map.of(
                "clearerName", "Alex", "clearerContact", "alex@example.test"));
        byte[] secondClaim = objectMapper.writeValueAsBytes(Map.of(
                "clearerName", "Sam", "clearerContact", "sam@example.test"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                    () -> claimStatus(orderId, firstClaim), executor);
            CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                    () -> claimStatus(orderId, secondClaim), executor);
            List<Integer> statuses = new ArrayList<>(List.of(
                    first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
            assertTrue(statuses.contains(200));
            assertTrue(statuses.contains(409));
            mockMvc.perform(get("/api/orders/{orderId}", orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLAIMED"));
            mockMvc.perform(get("/api/orders")).andExpect(jsonPath("$").isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    private String createOrderAndGetId() throws Exception {
        MvcResult result = mockMvc.perform(validOrder(null))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private int claimStatus(String orderId, byte[] requestBody) {
        try {
            return mockMvc.perform(post("/api/orders/{orderId}/claim", orderId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder validOrder(
            MockMultipartFile photo
    ) {
        return validOrder(photo, VALID_BOUNDARY);
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder validOrder(
            MockMultipartFile photo,
            String boundary
    ) {
        var request = multipart("/api/orders")
                .param("customerName", "Casey Customer")
                .param("customerContact", "casey@example.test")
                .param("address", "42 Snow Street")
                .param("notes", "Clear the front path")
                .param("boundary", boundary);
        if (photo != null) request.file(photo);
        return request;
    }

    private static MockMultipartFile image(String fileName, String contentType, byte[] bytes) {
        return new MockMultipartFile("photos", fileName, contentType, bytes);
    }

    private static byte[] createPng() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0x336699);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @TestConfiguration
    static class TestStorageConfiguration {
        @Bean
        @Primary
        FakePhotoStorage fakePhotoStorage() {
            return new FakePhotoStorage();
        }
    }

    static class FakePhotoStorage implements PhotoStorage {
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

        @Override
        public void put(String key, String contentType, byte[] bytes) {
            objects.put(key, bytes.clone());
        }

        @Override
        public byte[] get(String key) {
            byte[] bytes = objects.get(key);
            if (bytes == null) throw new IllegalStateException("No test object for key");
            return bytes.clone();
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }

        int size() {
            return objects.size();
        }

        void clear() {
            objects.clear();
        }
    }
}
