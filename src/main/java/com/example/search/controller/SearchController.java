package com.example.search.controller;

import com.example.search.security.TenantContext;
import com.example.search.service.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/search")
@Profile("api")
public class SearchController {
    private final TenantContext tenantContext;
    private final SearchService searchService;

    public SearchController(TenantContext tenantContext, SearchService searchService) {
        this.tenantContext = tenantContext;
        this.searchService = searchService;
    }

    @PostMapping
    public SearchResponse search(
            @Valid @RequestBody SearchRequest body,
            HttpServletRequest request) {
        return searchService.search(tenantContext.tenantId(request), body);
    }
}
