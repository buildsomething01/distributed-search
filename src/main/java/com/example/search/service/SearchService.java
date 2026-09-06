package com.example.search.service;

import com.example.search.controller.SearchRequest;
import com.example.search.controller.SearchResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("api")
public class SearchService {
    private final OpenSearchService openSearchService;

    public SearchService(OpenSearchService openSearchService) {
        this.openSearchService = openSearchService;
    }

    public SearchResponse search(String tenantId, SearchRequest request) {
        return openSearchService.search(tenantId, request);
    }
}
