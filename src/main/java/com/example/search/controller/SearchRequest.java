package com.example.search.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SearchRequest(
        @NotBlank String query,
        String documentType,
        String department,
        @Min(1) @Max(50) Integer limit) {

    public int limitOrDefault() {
        return limit == null ? 10 : limit;
    }
}
