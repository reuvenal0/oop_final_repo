package org.io.json;

/**
 * Describes how IDs and vectors are stored in the JSON objects.
 * Example object:
 * { "word": "the", "vector": [0.1, 0.2, ...] }
 */
public record JsonFormat(String idField, String vectorField) {

    public JsonFormat {
        if (idField == null || idField.isBlank()) {
            throw new IllegalArgumentException("idField must be non-empty");
        }
        if (vectorField == null || vectorField.isBlank()) {
            throw new IllegalArgumentException("vectorField must be non-empty");
        }
    }
}