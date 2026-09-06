package com.example.search.model;

import java.time.Instant;

public record DocumentEvent(
        Operation operation,
        String tenantId,
        String documentId,
        String title,
        String content,
        String documentType,
        String department,
        long version,
        Instant updatedAt) {

    public enum Operation {
        UPSERT,
        DELETE
    }
}
