package org.example.java26gruppblabb3;

public interface PhotoStorage {
    void put(String key, String contentType, byte[] bytes);

    byte[] get(String key);

    void delete(String key);
}
