package com.example.search.controller;

import java.util.List;
import java.util.Map;

public record SearchResponse(long tookMs, long total, List<Hit> hits) {
    public record Hit(String documentId, double score, Map<String, Object> document) {
    }
}
