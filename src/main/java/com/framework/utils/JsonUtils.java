package com.framework.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    public static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON: " + json, e);
        }
    }

    public static JsonNode readTreeFromClasspath(String classpathFile) {
        try (InputStream is = JsonUtils.class.getClassLoader().getResourceAsStream(classpathFile)) {
            if (is == null) {
                throw new IllegalArgumentException("File not found on classpath: " + classpathFile);
            }
            return MAPPER.readTree(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read JSON file: " + classpathFile, e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON into " + clazz.getSimpleName(), e);
        }
    }

    public static <T> List<T> fromJsonArrayClasspath(String classpathFile, Class<T> clazz) {
        try (InputStream is = JsonUtils.class.getClassLoader().getResourceAsStream(classpathFile)) {
            if (is == null) {
                throw new IllegalArgumentException("File not found on classpath: " + classpathFile);
            }
            var type = MAPPER.getTypeFactory().constructCollectionType(List.class, clazz);
            return MAPPER.readValue(is, type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read JSON array file: " + classpathFile, e);
        }
    }

    public static String toJson(Object object) {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to serialize object to JSON", e);
        }
    }

    public static void writeToFile(Object object, File file) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, object);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write JSON to file: " + file.getAbsolutePath(), e);
        }
    }

    /** Deep structural comparison — ignores key order, useful for response-vs-expected-payload checks. */
    public static boolean areEqual(String json1, String json2) {
        return readTree(json1).equals(readTree(json2));
    }
}
