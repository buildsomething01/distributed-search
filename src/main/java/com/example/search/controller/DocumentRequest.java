package com.example.search.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DocumentRequest(
        @NotBlank String documentId,
        @NotBlank String title,
        @NotBlank String content,
        String documentType,
        String department,
        @Min(1) long version) {
}
